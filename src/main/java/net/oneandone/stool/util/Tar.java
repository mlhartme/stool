/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.stool.util;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class Tar {
    public static FileNode toDir(World world, String tarStringOpt) throws IOException {
        FileNode result;

        result = world.getTemp().createTempDirectory();
        if (tarStringOpt != null) {
            try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(tarStringOpt));
                 GzipCompressorInputStream gz = new GzipCompressorInputStream(bytes);
                 TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {
                extract(tar, result);
            }
        }
        return result;
    }

    private static void extract(TarArchiveInputStream src, FileNode dest) throws IOException {
        for (TarArchiveEntry entry = src.getNextTarEntry(); entry != null; entry = src.getNextTarEntry()) {
            if (entry.isDirectory()) {
                dest.join(entry.getName()).mkdirsOpt();
            } else {
                dest.join(entry.getName()).copyFileFrom(src);
            }
        }
    }

    public static String fromDirOpt(FileNode dir) throws IOException {
        if (dir.list().isEmpty()) {
            return null;
        } else {
            return fromDir(dir);
        }
    }

    public static String fromDir(FileNode dir) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (GzipCompressorOutputStream gz = new GzipCompressorOutputStream(bytes);
             TarArchiveOutputStream dest = new TarArchiveOutputStream(gz)) {
            add(dir, dir, dest);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static void add(FileNode root, FileNode current, TarArchiveOutputStream dest) throws IOException {
        dest.putArchiveEntry(new TarArchiveEntry(current.toPath().toFile(), current.getRelative(root)));
        if (current.isFile()) {
            current.copyFileTo(dest);
            dest.closeArchiveEntry();
        } else {
            dest.closeArchiveEntry();
            for (FileNode child : current.list()) {
                add(root, child, dest);
            }
        }
    }

    private Tar() {
    }
}
