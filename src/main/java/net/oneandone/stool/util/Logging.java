package net.oneandone.stool.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.MultiOutputStream;
import net.oneandone.sushi.io.PrefixWriter;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

public class Logging {
    private final LoggerContext context;
    private final FileNode stool;
    private final String user;

    public Logging(FileNode stool, String user) throws IOException {
        Logger root;

        this.context = (LoggerContext) LoggerFactory.getILoggerFactory();
        this.stool = stool;
        this.user = user;

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
        return fileAppender("stoolAppender", stool, logger, "stool-%d{yyyy-MM-dd}.log.gz");
    }

    public RollingFileAppender stageAppender(FileNode file, String encoderName) throws IOException {
        return fileAppender("stageAppender", file, encoderName, "stool-%d{yyyy-MM-dd}.log.gz");
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
        logEncoder.setPattern("%date | %mdc | " + logger + " | " + user + " | %msg%n");
        logEncoder.start();
        return logEncoder;
    }

    public Logger lookup(String name) {
        return context.getLogger(name);
    }
}
