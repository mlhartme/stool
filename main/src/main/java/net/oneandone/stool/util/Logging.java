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

import net.oneandone.sushi.fs.Settings;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.MultiOutputStream;
import net.oneandone.sushi.io.PrefixWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Logging {
    private static final String EXTENSION = ".log";
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    public static Logging forLib(FileNode lib, String user) throws IOException {
        return create(lib.join("logs"), "stool", user);
    }

    public static Logging create(FileNode dir, String name, String user) throws IOException {
        String today;
        String id;
        Logging result;

        today = DATE_FORMAT.format(LocalDate.now());
        id = today + '-' + Integer.toString(id(dir, today));
        result = new Logging(id, dir.join(name + EXTENSION), user);
        return result;
    }

    public final String id;
    private final FileNode file;
    private final String user;

    private String stageId;
    private String stageName;

    public Logging(String id, FileNode file, String user) throws IOException {
        this.id = id;
        this.file = file;
        this.user = user;
        setStage("", "");
        if (!file.exists()) {
            file.writeBytes();
            Files.stoolFile(file);
        }
    }

    public void setStage(String id, String name) {
        stageId = id;
        stageName = name;
    }

    public void log(String logger, String message) {
        char c;

        try (Writer writer = file.newAppender()) {
            writer.append(LogEntry.TIME_FMT.format(LocalDateTime.now())).append('|');
            writer.append(id).append('|');
            writer.append(logger).append('|');
            writer.append(user).append('|');
            writer.append(stageId).append('|');
            writer.append(stageName).append('|');
            for (int i = 0, max = message.length(); i < max; i++) {
                c = message.charAt(i);
                switch (c) {
                    case '\r':
                        writer.append("\\r");
                        break;
                    case '\n':
                        writer.append("\\n");
                        break;
                    case '\\':
                        writer.append("\\\\");
                        break;
                    default:
                        writer.append(c);
                        break;
                }
            }
            writer.append('\n');
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void error(String message, Throwable throwable) {
        log("ERROR", message);
        // TODO: throwable is ignored
    }

    public PrintWriter writer(OutputStream stream, String logger) {
        PrintWriter result;

        try {
            result = new PrintWriter(new OutputStreamWriter(MultiOutputStream.createTeeStream(stream, new LogOutputStream(this, logger)), Settings.UTF_8), true);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        // empty prefix is replaced by stage commands when iterating multiple stages:
        result = new PrefixWriter(result);
        return result;
    }

    //--

    /**
     * Unique id starting with 1 every day, bumped for every invocation.
     */
    private static int id(FileNode varRun, String prefix) throws IOException {
        int retries;
        FileNode lock;
        FileNode file;
        int id;
        String str;

        retries = 0;
        while (true) {
            lock = varRun.join("id.lock");
            try {
                lock.mkfile();
                break;
            } catch (IOException e) {
                retries++;
                if (retries > 10) {
                    throw new IOException("cannot create " + lock);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e1) {
                    break;
                }
            }
        }
        try {
            file = varRun.join("id");
            if (!file.exists()) {
                id = 1;
                touch(file);
            } else {
                str = file.readString();
                if (str.startsWith(prefix)) {
                    id = Integer.parseInt(str.substring(prefix.length())) + 1;
                } else {
                    id = 1;
                }
            }
            file.writeString(prefix + id);
            return id;
        } finally {
            lock.deleteFile();
        }
    }

    private static FileNode touch(FileNode file) throws IOException {
        if (!file.exists()) {
            file.mkfile();
            file.setPermissions("rw-rw----");
        }
        return file;
    }

    private FileNode directory() {
        return file.getParent();
    }

    /** @return alle COMMAND Log entries operating on the specified stage */
    public List<LogEntry> stageCommands(String stageId) throws Exception {
        LogEntry entry;
        Map<String, LogEntry> commands;
        LogEntry command;
        List<LogEntry> result;

        result = new ArrayList<>();
        commands = new HashMap<>();
        try (LogReader reader = LogReader.create(directory())) {
            while (true) {
                entry = reader.next();
                if (entry == null) {
                    break;
                }
                if (entry.logger.equals("COMMAND")) {
                    if (commands.put(entry.id, entry) != null) {
                        throw new IllegalStateException("duplicate id: " + entry.id);
                    }
                }
                if (entry.stageId.equals(stageId)) {
                    command = commands.remove(entry.id);
                    if (command != null) {
                        result.add(command);
                    }
                }
            }
        }
        return result;
    }

    public List<LogEntry> info(String stageId, String id) throws Exception {
        LogEntry entry;
        List<LogEntry> result;

        result = new ArrayList<>();
        try (LogReader reader = LogReader.create(directory())) {
            while (true) {
                entry = reader.next();
                if (entry == null) {
                    break;
                }
                if (entry.id.equals(id) && entry.stageId.equals(stageId)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }
}
