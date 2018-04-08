/*
 * Copyright (C) 2013-2018 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package global.namespace.archive.commons.compress;

import global.namespace.archive.api.ArchiveFileEntry;
import global.namespace.archive.api.ArchiveFileInput;
import global.namespace.fun.io.api.Socket;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Optional;

import static global.namespace.archive.commons.compress.CommonsCompress.archiveFileEntry;
import static java.util.Objects.requireNonNull;

/**
 * Adapts a {@link ZipFile} to an {@link ArchiveFileInput}.
 *
 * @author Christian Schlichtherle
 */
final class ZipFileAdapter implements ArchiveFileInput<ZipArchiveEntry> {

    private final ZipFile zip;

    ZipFileAdapter(final ZipFile input) { this.zip = requireNonNull(input); }

    @Override
    public Iterator<ArchiveFileEntry<ZipArchiveEntry>> iterator() {
        return new Iterator<ArchiveFileEntry<ZipArchiveEntry>>() {

            final Enumeration<ZipArchiveEntry> en = zip.getEntries();

            @Override
            public boolean hasNext() { return en.hasMoreElements(); }

            @Override
            public ArchiveFileEntry<ZipArchiveEntry> next() { return archiveFileEntry(en.nextElement()); }

            @Override
            public void remove() { throw new UnsupportedOperationException(); }
        };
    }

    @Override
    public Optional<ArchiveFileEntry<ZipArchiveEntry>> entry(String name) {
        return Optional.ofNullable(zip.getEntry(name)).map(CommonsCompress::archiveFileEntry);
    }

    @Override
    public Socket<InputStream> input(ArchiveFileEntry<ZipArchiveEntry> entry) {
        return () -> zip.getInputStream(entry.entry());
    }

    @Override
    public void close() throws IOException { zip.close(); }
}