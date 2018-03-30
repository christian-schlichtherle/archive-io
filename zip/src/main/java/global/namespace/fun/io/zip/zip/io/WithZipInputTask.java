/*
 * Copyright (C) 2013 Schlichtherle IT Services & Stimulus Software.
 * All rights reserved. Use is subject to license terms.
 */
package global.namespace.fun.io.zip.zip.io;

import global.namespace.fun.io.zip.io.Closeables;

import java.io.File;
import java.io.IOException;

/**
 * @see ZipSources#execute
 * @author Christian Schlichtherle
 */
final class WithZipInputTask<V> implements ZipSources.ExecuteStatement<V> {

    private final ZipInputTask<V> task;

    WithZipInputTask(final ZipInputTask<V> task) { this.task = task; }

    @Override public V on(File file) throws Exception {
        return on(new ZipFileStore(file));
    }

    @Override public V on(ZipSource source) throws Exception {
        return Closeables.execute(task, source.input());
    }
}