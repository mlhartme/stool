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
package net.oneandone.stool.server.util;

import net.oneandone.sushi.fs.GetLastModifiedException;
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
import java.util.zip.GZIPInputStream;

public class LogReader {
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
    private List<String> lines;

    public LogReader(List<FileNode> files) {
        this.files = files;
        this.lines = null;
    }

    public long lastModified() throws GetLastModifiedException {
        return files.get(files.size() - 1).getLastModified();
    }

    public LogEntry first() throws IOException {
        String line;

        for (FileNode file : files) {
            try (LineReader reader = open(file)) {
                line = reader.next();
                if (line != null) {
                    return LogEntry.parse(line);
                }
            }
        }
        return null;
    }

    public LogEntry prev() throws IOException {
        String line;
        LogEntry result;
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
                result = LogEntry.parse(lines.remove(size - 1));
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