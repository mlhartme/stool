package net.oneandone.stool.util;

import net.oneandone.sushi.fs.LineFormat;
import net.oneandone.sushi.fs.LineReader;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Comparator;
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
        Collections.sort(files, (left, right) -> {
            if (current(left)) {
                return -1;
            } else if (current(right)) {
                return 1;
            } else {
                return left.getName().compareTo(right.getName());
            }
        });
        return new LogReader(files);
    }

    private static boolean current(FileNode log) {
        return log.getName().equals(log);
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
