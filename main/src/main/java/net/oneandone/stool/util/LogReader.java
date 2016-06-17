/**
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

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.LineFormat;
import net.oneandone.sushi.io.LineReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class LogReader implements AutoCloseable {
    public static LogReader create(FileNode directory) throws IOException {
        List<FileNode> files;
        Iterator<FileNode> iter;
        FileNode file;

        files = directory.list();
        iter = files.iterator();
        while (iter.hasNext()) {
            file = iter.next();
            if (file.isDirectory()) {
                iter.remove();
            } else if (!file.getName().endsWith(".log") && !file.getName().endsWith(".log.gz")) {
                iter.remove();
            }
        }
        Collections.sort(files, (left, right) -> left.getName().compareTo(right.getName()));
        return new LogReader(files);
    }

    private final List<FileNode> files;
    private LineReader reader;

    public LogReader(List<FileNode> files) {
        this.files = files;
        this.reader = null;
    }

    public LogEntry next() throws IOException {
        FileNode file;
        String line;
        InputStream src;

        while (true) {
            if (reader == null) {
                if (files.isEmpty()) {
                    return null;
                }
                file = files.remove(0);
                src = file.newInputStream();
                if (file.getName().endsWith(".gz")) {
                    src = new GZIPInputStream(src);
                }
                reader = new LineReader(new InputStreamReader(src, "utf8"), LineFormat.RAW_FORMAT);
            }
            line = reader.next();
            if (line != null) {
                return LogEntry.parse(line);
            }
            reader.close();
            reader = null;
        }
    }

    @Override
    public void close() throws Exception {
        if (reader != null) {
            reader.close();
        }
    }
}
