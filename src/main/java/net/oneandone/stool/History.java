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
package net.oneandone.stool;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.LogEntry;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.fs.LineFormat;
import net.oneandone.sushi.fs.LineReader;
import net.oneandone.sushi.fs.file.FileNode;
import org.joda.time.format.DateTimeFormat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class History extends StageCommand {
    public History(Session session) {
        super(session);
    }

    @Option("max")
    private int max = 999;

    private int detail = -1;

    @Remaining
    public void remaining(String str) {
        detail = Integer.parseInt(str);
        max = detail+1;
    }


    @Override
    public void doInvoke(Stage s) throws Exception {
        String stageId;
        LogEntry entry;
        int counter ;
        String id;

        stageId = s.config().id;
        try (LogReader reader = LogReader.create(session.home.join("logs"))) {
            counter = 1;
            id = null;
            while (true) {
                entry = reader.next();
                if (entry == null) {
                    break;
                }
                if (entry.id.equals(stageId)) {
                    if (entry.logger.equals("IN")) {
                        if (detail == -1 || detail == counter) {
                            console.info.println("[" + counter + "] " + entry.dateTime.toString(DateTimeFormat.shortDateTime())
                                    + " " + entry.user + ": " + entry.message);
                        }
                        if (detail == counter) {
                            id = entry.id;
                        }
                        counter++;
                    }
                    if (entry.id.equals(id)) {
                        console.info.println("     " + entry.message);
                    }

                    if (counter > max) {
                        break;
                    }
                }
            }
        }
    }

    public static class LogReader implements AutoCloseable {
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
                } else if (!current(file) && !file.getName().endsWith(".log.gz")) {
                    iter.remove();
                }
            }
            Collections.sort(files, new Comparator<FileNode>() {
                @Override
                public int compare(FileNode left, FileNode right) {
                    if (current(left)) {
                        return -1;
                    } else if (current(right)) {
                        return 1;
                    } else {
                        return left.getName().compareTo(right.getName());
                    }
                }
            });
            System.out.println("sorted: " + files);
            return new LogReader(files);
        }

        private static boolean current(FileNode log) {
            return log.getName().equals(log);
        }

        private final List<FileNode> files;
        private LineReader reader;

        public LogReader(List<FileNode> files)  {
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
                    src = file.createInputStream();
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
}
