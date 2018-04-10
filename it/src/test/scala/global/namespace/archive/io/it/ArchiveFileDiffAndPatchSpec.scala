/*
 * Copyright (C) 2013-2018 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package global.namespace.archive.io.it

import java.io._
import java.security.MessageDigest

import global.namespace.archive.io.api.{ArchiveFileSource, ArchiveFileStore}
import global.namespace.archive.io.commons.compress.Compress
import global.namespace.archive.io.delta.Delta._
import global.namespace.archive.io.it.ArchiveFileDiffAndPatchSpec._
import global.namespace.archive.io.juz.JUZ
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.prop.PropertyChecks._
import org.scalatest.prop.TableFor1

import scala.collection.JavaConverters._

/** @author Christian Schlichtherle */
class ArchiveFileDiffAndPatchSpec extends WordSpec {

  "An archive file diff" should {
    "correctly partition the entry names and digests" in {
      forAllArchives { (a, b) => { _ =>
        val model = (diff base a update b digest sha1).deltaModel
        import model._
        changedEntries.asScala map (_.name) shouldBe List("differentEntrySize")
        addedEntries.asScala map (_.name) shouldBe List("entryOnlyInFile2")
        removedEntries.asScala map (_.name) shouldBe List("entryOnlyInFile1")
        unchangedEntries.asScala map (_.name) shouldBe List("META-INF/MANIFEST.MF", "differentEntryTime", "equalEntry")
      }}
    }
  }

  "An archive file diff/patch" should {
    "support round-trip diffing/patching archive files" in {
      forAllArchives { (a, b) => { implicit store =>
        withTempArchiveStore { c =>
          withTempArchiveStore { b2 =>

            diff base a update b digest md5 to c
            patch base a delta c to b2

            val unchangedReference: List[String] = {
              b applyReader (_.asScala.filter(!_.isDirectory).map(_.name).toList)
            }

            val model = (diff base b update b2 digest sha1).deltaModel
            model.changedEntries shouldBe empty
            model.addedEntries shouldBe empty
            model.removedEntries shouldBe empty
            model.unchangedEntries.asScala map (_.name) shouldBe unchangedReference
          }
        }
      }}
    }
  }
}

private object ArchiveFileDiffAndPatchSpec {

  def forAllArchives(test: (ArchiveFileSource[_], ArchiveFileSource[_]) => (File => ArchiveFileStore[_]) => Any): Unit = {
    forAll(Tests)(store => test(store(Test1JarFile), store(Test2JarFile))(store))
  }

  val Tests: TableFor1[File => ArchiveFileStore[_]] = Table(
    "archive file store factory",
    Compress.jar,
    Compress.zip,
    JUZ.jar,
    JUZ.zip
  )

  def withTempArchiveStore(test: ArchiveFileStore[_] => Any)(implicit fun: File => ArchiveFileStore[_]): Unit = {
    val file = File.createTempFile("temp", null)
    try {
      test(fun(file))
    } finally {
      file delete ()
    }
  }

  private lazy val Test1JarFile = resourceFile("test1.jar")

  private lazy val Test2JarFile = resourceFile("test2.jar")

  private def resourceFile(resourceName: String): File = new File((classOf[ArchiveFileDiffAndPatchSpec] getResource resourceName).toURI)

  def sha1: MessageDigest = MessageDigest getInstance "SHA-1"

  def md5: MessageDigest = MessageDigest getInstance "MD5"
}
