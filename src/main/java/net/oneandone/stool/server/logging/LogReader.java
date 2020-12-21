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
package net.oneandone.stool.server.logging;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.LineFormat;
import net.oneandone.sushi.io.LineReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

public class LogReader<T> {
    public static LogReader<AccessLogEntry> accessLog(FileNode directory) throws IOException {
        return create(AccessLogEntry::parse, "access-", directory);
    }

    private static <T> LogReader<T> create(Function<String, T> parser, String prefix, FileNode directory) throws IOException {
        List<FileNode> files;
        Iterator<FileNode> iter;
        FileNode file;
        String name;

        files = directory.list();
        iter = files.iterator();
        while (iter.hasNext()) {
            file = iter.next();
            if (file.isDirectory()) {
                iter.remove();
            } else {
                name = file.getName();
                if (!(name.startsWith(prefix) && (name.endsWith(".log") || name.endsWith(".log.gz")))) {
                    iter.remove();
                }
            }
        }
        Collections.sort(files, (left, right) -> left.getName().compareTo(right.getName()));
        return new LogReader(parser, files);
    }

    private final Function<String, T> parser;
    private final List<FileNode> files;
    private List<String> lines;

    public LogReader(Function<String, T> parser, List<FileNode> files) {
        this.parser = parser;
        this.files = files;
        this.lines = null;
    }

    public T first() throws IOException {
        String line;

        for (FileNode file : files) {
            try (LineReader reader = open(file)) {
                line = reader.next();
                if (line != null) {
                    return parser.apply(line);
                }
            }
        }
        return null;
    }

    public T prev() throws IOException {
        String line;
        T result;
        int size;

        while (true) {
            if (lines == null) {
                size = files.size();
                if (size == 0) {
                    return null;
                }
                try (LineReader reader = open(files.remove(size - 1))) {
                    lines = new ArrayList<>();
                    while (true) {
                        line = reader.next();
                        if (line == null) {
                            break;
                        }
                        lines.add(line);
                    }
                }
            }
            size = lines.size();
            if (size > 0) {
                result = parser.apply(lines.remove(size - 1));
                return result;
            } else {
                lines = null;
            }
        }
    }

    private LineReader open(FileNode file) throws IOException {
        InputStream src;

        src = file.newInputStream();
        if (file.getName().endsWith(".gz")) {
            src = new GZIPInputStream(src);
        }
        return new LineReader(new InputStreamReader(src, "utf8"), LineFormat.RAW_FORMAT);
    }
}
