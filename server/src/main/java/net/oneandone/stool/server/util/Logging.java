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

import net.oneandone.sushi.io.PrefixWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

/** Simply log to a file - there's no logback or log4j involved */
public abstract class Logging {
    private static final Logger DETAILS = LoggerFactory.getLogger("DETAILS");


    public static void info(String message) {
        logs("INFO", message);
    }

    public static void verbose(String message) {
        logs("VERBOSE", message);
    }

    public static void verbose(String message, Throwable throwable) {
        logs("VERBOSE", message);
        logs("VERBOSE", stacktrace(throwable));
    }

    public static void error(String message, Throwable throwable) {
        logs("ERROR", message);
        logs("ERROR", stacktrace(throwable));
    }

    public static void error(String message) {
        logs("ERROR", message);
    }

    //--

    /** this is the counter-part of the LogEntry.parse method */
    private static void logs(String logger, String message) {
        LogEntry entry;

        entry = new LogEntry(LocalDateTime.now(), MDC.get("client-invocation"), logger, MDC.get("user"), MDC.get("stage"), message);
        DETAILS.info(entry.toString());
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
}
