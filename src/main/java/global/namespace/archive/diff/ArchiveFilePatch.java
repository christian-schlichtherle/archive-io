/*
 * Copyright (C) 2013-2018 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package global.namespace.archive.diff;

import global.namespace.archive.diff.model.DeltaModel;
import global.namespace.archive.diff.model.EntryNameAndDigestValue;
import global.namespace.archive.diff.spi.ArchiveFileInput;
import global.namespace.archive.diff.spi.ArchiveFileOutput;
import global.namespace.archive.diff.spi.ArchiveFileSink;
import global.namespace.archive.diff.spi.ArchiveFileSource;
import global.namespace.fun.io.api.Sink;
import global.namespace.fun.io.api.Socket;
import global.namespace.fun.io.api.function.XConsumer;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Optional;

import static global.namespace.archive.diff.Archive.entrySource;
import static global.namespace.archive.diff.Copy.copy;
import static global.namespace.archive.diff.model.DeltaModel.ENTRY_NAME;
import static global.namespace.archive.diff.model.DeltaModel.decodeFromXml;

/**
 * Patches a first archive file to a second archive file using a delta archive file.
 *
 * @author Christian Schlichtherle
 */
abstract class ArchiveFilePatch {

    abstract ArchiveFileSource firstSource();

    abstract ArchiveFileSource deltaSource();

    void to(ArchiveFileSink second) throws Exception {
        accept(engine -> second.acceptWriter(engine::patchTo));
    }

    private void accept(final XConsumer<Engine> consumer) throws Exception {
        firstSource().acceptReader(firstInput -> deltaSource().acceptReader(deltaInput -> consumer.accept(
                new Engine() {

                    ArchiveFileInput firstInput() { return firstInput; }

                    ArchiveFileInput deltaInput() { return deltaInput; }
                }
        )));
    }

    private abstract static class Engine {

        DeltaModel model;

        abstract ArchiveFileInput firstInput();

        abstract ArchiveFileInput deltaInput();

        void patchTo(final ArchiveFileOutput secondOutput) throws Exception {
            for (EntryNameFilter filter : passFilters(secondOutput)) {
                patchTo(secondOutput, new NoDirectoryEntryNameFilter(filter));
            }
        }

        /**
         * Returns a list of filters for the different passes required to process the to-archive file.
         * At least one filter is required to output anything.
         * The filters should properly partition the set of entry sources, i.e. each entry source should be accepted by
         * exactly one filter.
         */
        EntryNameFilter[] passFilters(final ArchiveFileOutput secondOutput) {
            if (secondOutput.entry("") instanceof JarArchiveEntry) {
                // The JarInputStream class assumes that the file entry
                // "META-INF/MANIFEST.MF" should either be the first or the second
                // entry (if preceded by the directory entry "META-INF/"), so we
                // need to process the delta-archive file in two passes with a
                // corresponding filter to ensure this order.
                // Note that the directory entry "META-INF/" is always part of the
                // unchanged delta set because it's content is always empty.
                // Thus, by copying the unchanged entries before the changed
                // entries, the directory entry "META-INF/" will always appear
                // before the file entry "META-INF/MANIFEST.MF".
                final EntryNameFilter manifestFilter = new ManifestEntryNameFilter();
                return new EntryNameFilter[] { manifestFilter, new InverseEntryNameFilter(manifestFilter) };
            } else {
                return new EntryNameFilter[] { new AcceptAllEntryNameFilter() };
            }
        }

        void patchTo(final ArchiveFileOutput secondOutput, final EntryNameFilter filter) throws Exception {

            class MyArchiveEntrySink implements Sink {

                private final EntryNameAndDigestValue entryNameAndDigest;

                MyArchiveEntrySink(final EntryNameAndDigestValue entryNameAndDigest) {
                    assert null != entryNameAndDigest;
                    this.entryNameAndDigest = entryNameAndDigest;
                }

                @Override
                public Socket<OutputStream> output() {
                    final ArchiveEntry secondEntry = secondEntry(entryNameAndDigest.entryName());
                    return secondSink(secondEntry).map(out -> {
                        final MessageDigest digest = digest();
                        digest.reset();
                        return new DigestOutputStream(out, digest) {

                            @Override
                            public void close() throws IOException {
                                super.close();
                                if (!valueOfDigest().equals(entryNameAndDigest.digestValue())) {
                                    throw new WrongMessageDigestException(entryNameAndDigest.entryName());
                                }
                            }

                            String valueOfDigest() { return MessageDigests.valueOf(digest); }
                        };
                    });
                }

                private ArchiveEntry secondEntry(String name) { return secondOutput.entry(name); }

                private Socket<OutputStream> secondSink(ArchiveEntry secondEntry) {
                    return secondOutput.output(secondEntry);
                }
            }

            abstract class Patch {

                abstract ArchiveFileInput input();

                abstract IOException ioException(Throwable cause);

                final <T> void apply(final Transformation<T> transformation, final Iterable<T> iterable)
                        throws Exception {
                    for (final T item : iterable) {
                        final EntryNameAndDigestValue entryNameAndDigestValue = transformation.apply(item);
                        final String name = entryNameAndDigestValue.entryName();
                        if (!filter.accept(name)) {
                            continue;
                        }
                        final Optional<ArchiveEntry> entry = input().entry(name);
                        try {
                            copy(
                                    entrySource(entry.orElseThrow(() ->
                                            ioException(new MissingArchiveEntryException(name))), input()),
                                    new MyArchiveEntrySink(entryNameAndDigestValue)
                            );
                        } catch (WrongMessageDigestException e) {
                            throw ioException(e);
                        }
                    }
                }
            }

            class FirstArchiveFilePatch extends Patch {

                @Override
                ArchiveFileInput input() { return firstInput(); }

                @Override
                IOException ioException(Throwable cause) { return new WrongFirstArchiveFileException(cause); }
            }

            class DeltaArchiveFilePatch extends Patch {

                @Override
                ArchiveFileInput input() { return deltaInput(); }

                @Override
                IOException ioException(Throwable cause) { return new InvalidDeltaArchiveFileException(cause); }
            }

            // Order is important here!
            new FirstArchiveFilePatch().apply(new IdentityTransformation(), model().unchangedEntries());
            new DeltaArchiveFilePatch().apply(new EntryNameAndDigest2Transformation(), model().changedEntries());
            new DeltaArchiveFilePatch().apply(new IdentityTransformation(), model().addedEntries());
        }

        MessageDigest digest() throws Exception {
            return MessageDigest.getInstance(model().digestAlgorithmName());
        }

        DeltaModel model() throws Exception {
            final DeltaModel model = this.model;
            return null != model ? model : (this.model = loadModel());
        }

        DeltaModel loadModel() throws Exception {
            return decodeFromXml(entrySource(modelArchiveEntry(), deltaInput()));
        }

        ArchiveEntry modelArchiveEntry() throws Exception {
            return deltaInput().entry(ENTRY_NAME).orElseThrow(() ->
                    new InvalidDeltaArchiveFileException(new MissingArchiveEntryException(ENTRY_NAME)));
        }
    }
}