/*
 * Copyright (C) 2013-2018 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package global.namespace.archive.juz;

import global.namespace.archive.api.ArchiveEntrySink;
import global.namespace.archive.api.ArchiveEntrySource;
import global.namespace.archive.api.ArchiveFileOutput;
import global.namespace.fun.io.api.Socket;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.Objects.requireNonNull;

/**
 * Adapts a {@link ZipOutputStream} to an {@link ArchiveFileOutput}.
 *
 * @author Christian Schlichtherle
 */
class ZipOutputStreamAdapter implements ArchiveFileOutput<ZipEntry> {

    private static final Pattern COMPRESSED_FILE_EXTENSIONS =
            Pattern.compile(".*\\.(ear|jar|war|zip|gz|xz)", Pattern.CASE_INSENSITIVE);

    private final ZipOutputStream zip;

    ZipOutputStreamAdapter(final ZipOutputStream zip) { this.zip = requireNonNull(zip); }

    /** Returns {@code false}. */
    public boolean isJar() { return false; }

    public ArchiveEntrySink<ZipEntry> sink(String name) { return sink(new ZipEntry(name)); }

    ArchiveEntrySink<ZipEntry> sink(ZipEntry entry) {
        return new ArchiveEntrySink<ZipEntry>() {

            public String name() { return entry.getName(); }

            public boolean isDirectory() { return entry.isDirectory(); }

            public ZipEntry entry() { return entry; }

            public Socket<OutputStream> output(final ArchiveEntrySource<?> source) {
                // TODO: This should be deferred until the underlying socket is used to create an output stream.
                if (source.entry() instanceof ZipEntry && COMPRESSED_FILE_EXTENSIONS.matcher(source.name()).matches()) {
                    final ZipEntry origin = (ZipEntry) source.entry();
                    final long size = origin.getSize();

                    entry.setMethod(ZipOutputStream.STORED);
                    entry.setSize(size);
                    entry.setCompressedSize(size);
                    entry.setCrc(origin.getCrc());
                }
                return output();
            }

            public Socket<OutputStream> output() {
                return () -> {
                    if (entry.isDirectory()) {
                        entry.setMethod(ZipOutputStream.STORED);
                        entry.setSize(0);
                        entry.setCompressedSize(0);
                        entry.setCrc(0);
                    }
                    zip.putNextEntry(entry);
                    return new FilterOutputStream(zip) {

                        @Override
                        public void close() throws IOException { ((ZipOutputStream) out).closeEntry(); }
                    };
                };
            }
        };
    }

    @Override
    public void close() throws IOException { zip.close(); }
}
