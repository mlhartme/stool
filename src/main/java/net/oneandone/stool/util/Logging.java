package net.oneandone.stool.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.MultiOutputStream;
import net.oneandone.sushi.io.PrefixWriter;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logging {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyMMdd-");

    public static Logging forHome(FileNode home, String user) throws IOException {
        String today;
        String id;
        Logging result;

        today = DATE_FORMAT.format(new Date());
        id = Integer.toString(id(home.join("logs"), today));
        result = new Logging(id, home.join("logs/stool.log"), user);
        result.configureRootLogger();
        return result;
    }

    private final String id;
    private final LoggerContext context;
    private final FileNode stool;
    private final String user;

    public Logging(String id, FileNode stool, String user) {
        this.id = id;
        this.context = (LoggerContext) LoggerFactory.getILoggerFactory();
        this.stool = stool;
        this.user = user;
    }

    public void configureRootLogger() throws IOException {
        Logger root;

        // adjust the default configuration
        root = context.getLogger("ROOT");
        root.detachAndStopAllAppenders();
        root.addAppender(stoolAppender("OTHER"));
        root.setLevel(Level.INFO);
    }

    public PrintWriter writer(OutputStream stream, String logger) throws IOException {
        PrintWriter result;

        result = new PrintWriter(MultiOutputStream.createTeeStream(stream, new Slf4jOutputStream(logger(logger), false)), true);
        // empty prefix is replaced by stage commands when iterating multiple stages:
        result = new PrefixWriter(result);
        return result;
    }

    public Logger logger(String name) throws IOException {
        Logger result;

        result = context.getLogger(name);
        result.setAdditive(false);
        result.setLevel(Level.INFO);
        result.addAppender(stoolAppender(name));
        return result;
    }

    private RollingFileAppender stoolAppender(String logger) throws IOException {
        return fileAppender("stoolAppender", stool, logger, stool.getParent().getAbsolute() + "/stool-%d{yyyy-MM-dd}.log.gz");
    }

    public RollingFileAppender stageAppender(FileNode file, String encoderName) throws IOException {
        return fileAppender("stageAppender", file, encoderName, file.getParent().getAbsolute() + "/stool-%d{yyyy-MM-dd}.log.gz");
    }

    private RollingFileAppender fileAppender(String name, FileNode file, String encoderName, String pattern) throws IOException {
        RollingFileAppender result;
        TimeBasedRollingPolicy policy;

        result = new RollingFileAppender();
        result.setContext(context);
        result.setName(name);
        result.setEncoder(encoder(encoderName));
        result.setAppend(true);
        result.setFile(file.getAbsolute());

        policy = new TimeBasedRollingPolicy();
        policy.setContext(context);
        policy.setParent(result);
        policy.setFileNamePattern(pattern);
        policy.setMaxHistory(7);
        policy.start();

        result.setRollingPolicy(policy);
        result.start();

        if (!file.exists()) {
            file.writeBytes();
            Files.stoolFile(file);
        }
        return result;
    }

    private PatternLayoutEncoder encoder(String logger) {
        PatternLayoutEncoder logEncoder = new PatternLayoutEncoder();
        logEncoder.setContext(context);
        logEncoder.setPattern("%date | " + id + " | " + logger + " | " + user + " | %msg%n");
        logEncoder.start();
        return logEncoder;
    }

    public Logger lookup(String name) {
        return context.getLogger(name);
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

    public Logger errorTool(String url) throws MalformedURLException {
        Logger logger;
        Appender appender;

        logger = context.getLogger("errortool");
        if (url == null) {
            logger.setLevel(Level.OFF);
        } else {
            appender = new ErrorToolAppender(new URL(url));
            appender.start();
            logger.addAppender(appender);
        }
        return logger;
    }
}
