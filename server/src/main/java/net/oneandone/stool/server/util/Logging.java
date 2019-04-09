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

import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.PrefixWriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.time.LocalDateTime;

/** Simply log to a file - there's no logback or log4j involved */
public class Logging {
    private static final String EXTENSION = ".log";

    public static Logging forHome(FileNode home, String user) throws IOException {
        return create(home.join("logs"), "stool", user);
    }

    public static Logging create(FileNode dir, String name, String user) throws IOException {
        return new Logging(logFile(dir, name), user);
    }

    private final FileNode file;
    private final String user;

    /** unique id for this invocation */
    private String clientInvocation;
    private String stageName;
    private FileNode stageFile;

    public Logging(FileNode file, String user) throws IOException {
        this.file = file;
        this.user = user;

        this.clientInvocation = "";
        this.stageName = "";
        this.stageFile = null;
        if (!file.exists()) {
            file.writeBytes();
        }
    }

    //--


    public void info(String message) {
        log("INFO", message);
    }

    public void verbose(String message) {
        log("VERBOSE", message);
    }

    public void verbose(String message, Throwable throwable) {
        log("VERBOSE", message);
        log("VERBOSE", stacktrace(throwable));
    }

    public void error(String message, Throwable throwable) {
        log("ERROR", message);
        log("ERROR", stacktrace(throwable));
    }

    public void error(String message) {
        log("ERROR", message);
    }

    //--

    public void init(String stage, String clientInvocation, String clientCommand) throws MkdirException {
        if (stageFile != null) {
            throw new IllegalStateException("stage already open: " + stageFile.getAbsolute());
        }
        this.clientInvocation = clientInvocation;
        this.stageName = stage;
        this.stageFile = logFile(directory().join(stage).mkdirOpt(), "stool");

        log("COMMAND", clientCommand);
    }

    private static FileNode logFile(FileNode dir, String base) {
        String date;

        date = LogEntry.DATE_FMT.format(LocalDateTime.now());
        return dir.join(base + "-" + date + EXTENSION);
    }

    public void closeStage() {
        if (stageFile == null) {
            throw new IllegalStateException("stage already closed");
        }
        stageFile = null;
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

        writer.append(LogEntry.DATE_FMT.format(LocalDateTime.now())).append('|');
        writer.append(clientInvocation).append('|');
        writer.append(logger).append('|');
        writer.append(user).append('|');
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

    private static String stacktrace(Throwable throwable) {
        StringWriter dest;
        PrintWriter pw;

        dest = new StringWriter();
        pw = new PrefixWriter(dest);
        throwable.printStackTrace(pw);
        pw.close();
        return dest.toString();
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
