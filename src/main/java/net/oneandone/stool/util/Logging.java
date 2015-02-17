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
    private final FileNode logs;
    private final String user;

    public Logging(FileNode logs, String user) {
        Logger root;

        this.context = (LoggerContext) LoggerFactory.getILoggerFactory();
        this.logs = logs;
        this.user = user;

        // adjust the default configuration
        root = context.getLogger("ROOT");
        root.detachAndStopAllAppenders();
        root.addAppender(fileAppender("OTHER"));
        root.setLevel(Level.INFO);
    }

    public void fixPermissions() throws IOException {
        FileNode file;

        file = logs.join("stool.log");
        if (!file.exists()) {
            file.writeBytes();
            Files.stoolFile(file);
        }
    }

    public PrintWriter writer(OutputStream stream, String logger) {
        PrintWriter result;

        result = new PrintWriter(MultiOutputStream.createTeeStream(stream, new Slf4jOutputStream(logger(logger), false)), true);
        // empty prefix is replaced by stage commands when iterating multiple stages:
        result = new PrefixWriter(result);
        return result;
    }

    public Logger logger(String name) {
        Logger result;

        result = context.getLogger(name);
        result.setAdditive(false);
        result.setLevel(Level.INFO);
        result.addAppender(fileAppender(name));
        return result;
    }

    private RollingFileAppender fileAppender(String logger) {
        RollingFileAppender result;
        TimeBasedRollingPolicy policy;

        result = new RollingFileAppender();

        policy = new TimeBasedRollingPolicy();
        policy.setContext(context);
        policy.setParent(result);
        policy.setFileNamePattern(logs.join("stool-%d{yyyy-MM-dd}.log.gz").getAbsolute());
        policy.setMaxHistory(7);
        policy.start();

        result.setContext(context);
        result.setName("logFile");
        result.setEncoder(encoder(logger));
        result.setAppend(true);
        result.setFile(logs.join("stool.log").getAbsolute());

        result.setRollingPolicy(policy);
        result.start();
        return result;
    }

    private PatternLayoutEncoder encoder(String logger) {
        PatternLayoutEncoder logEncoder = new PatternLayoutEncoder();
        logEncoder.setContext(context);
        logEncoder.setPattern("%date | %mdc | " + logger + " | " + user + " | %msg%n");
        logEncoder.start();
        return logEncoder;
    }
}
