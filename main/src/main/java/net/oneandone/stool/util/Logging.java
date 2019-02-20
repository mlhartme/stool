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

import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.Settings;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.MultiOutputStream;
import net.oneandone.sushi.io.PrefixWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Stool logging is just saving console output to log files - there's no logback or log4j involved */
public class Logging {
    private static final String EXTENSION = ".log";
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    public static Logging forHome(FileNode home, String user) throws IOException {
        return create(home.join("logs"), "stool", user);
    }

    public static Logging create(FileNode dir, String name, String user) throws IOException {
        String date;
        String prefix;
        String id;
        Logging result;

        date = DATE_FORMAT.format(LocalDate.now());
        prefix = date + ".";
        id = prefix + Integer.toString(id(dir, prefix));
        result = new Logging(id, logFile(dir, name), user);
        return result;
    }

    /** unique id for this invocation */
    public final String id;
    private final FileNode file;
    private final String user;

    private String command;

    private String stageId;
    private String stageName;
    private FileNode stageFile;

    public Logging(String id, FileNode file, String user) throws IOException {
        this.id = id;
        this.file = file;
        this.user = user;
        this.stageId = "";
        this.stageName = "";
        this.stageFile = null;
        if (!file.exists()) {
            file.writeBytes();
        }
    }

    public void command(String command) {
        this.command = command;
        log("COMMAND", command);
    }

    public void openStage(String id, String name) throws MkdirException {
        if (stageFile != null) {
            throw new IllegalStateException("stage already open: " + stageFile.getAbsolute());
        }
        stageId = id;
        stageName = name;
        stageFile = logFile(directory().join(id).mkdirOpt(), "stool");
        command(command);
    }

    private static FileNode logFile(FileNode dir, String base) {
        String date;

        date = DATE_FORMAT.format(LocalDate.now());
        return dir.join(base + "-" + date + EXTENSION);
    }

    public void closeStage() {
        if (stageFile == null) {
            throw new IllegalStateException("stage already closed");
        }
        stageFile = null;
        stageId = "";
        stageName = "";
    }

    /** this is the counter-part of the LogEntry.parse method */
    public void log(String logger, String message) {
        try (Writer writer = (stageFile != null ? stageFile : file).newAppender()) {
            logEntry(logger, message, writer);
        } catch (IOException e) {
            throw new RuntimeException("cannot write log file: " + e.getMessage(), e);
        }
    }

    private void logEntry(String logger, String message, Writer writer) throws IOException {
        char c;

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
    }

    public void error(String message, Throwable throwable) {
        log("ERROR", message);
        log("ERROR", stacktrace(throwable));
    }

    private static String stacktrace(Throwable throwable) {
        StringWriter dest;
        PrintWriter pw;

        dest = new StringWriter();
        pw = new PrefixWriter(dest);
        throwable.printStackTrace(pw);
        pw.close();
        return dest.toString();
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

    private static void touch(FileNode file) throws IOException {
        if (!file.exists()) {
            file.mkfile();
        }
    }

    public FileNode directory() {
        return file.getParent();
    }

    public String getUser() {
        return user;
    }

    public void rotate() throws IOException {
        long daymillies = 1000L * 60 * 60 * 24;
        long manydaysmillies = daymillies * 90;
        long now;

        now = System.currentTimeMillis();
        for (FileNode f : file.getParent().find("*.log")) {
            if (now - f.getLastModified() > daymillies) {
                f.gzip(f.getParent().join(f.getName() + ".gz"));
                f.deleteFile();
            }
        }
        for (FileNode f : file.getParent().find("*.log.gz")) {
            if (now - f.getLastModified() > manydaysmillies) {
                f.deleteFile();
            }
        }
    }
}
