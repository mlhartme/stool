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

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class LogEntry {
    public static LogEntry forEvent(ILoggingEvent event) {
        Instant instant;
        LocalDateTime date;
        Map<String, String> mdc;

        instant = Instant.ofEpochMilli(event.getTimeStamp());
        date = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        mdc = event.getMDCPropertyMap();
        return new LogEntry(date, mdc.get("client-invocation"), "COMMAND", mdc.get("user"), mdc.get("stage"), mdc.get("client-command"));
    }

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss,SSS");

    /** Count-part of the Logging.log method. */
    public static LogEntry parse(String line) {
        int len;

        int date;
        int client;
        int logger;
        int user;
        int stage;

        len = line.length();

        // CAUTION: do not use split, because messages may contain separators
        date = line.indexOf('|');
        client = line.indexOf('|', date + 1); // invocation id
        logger = line.indexOf('|', client + 1);
        user = line.indexOf('|', logger + 1);
        stage = line.indexOf('|', user + 1);
        if (line.charAt(len - 1) != '\n') {
            throw new IllegalArgumentException(line);
        }

        return new LogEntry(
                LocalDateTime.parse(line.substring(0, date), LogEntry.DATE_FMT),
                line.substring(date + 1, client),
                line.substring(client + 1, logger),
                line.substring(logger + 1, user),
                line.substring(user + 1, stage),
                unescape(line.substring(stage + 1, len -1)));
    }

    private static String unescape(String message) {
        StringBuilder builder;
        char c;
        int max;

        if (message.indexOf('\\') == -1) {
            return message;
        } else {
            max = message.length();
            builder = new StringBuilder(max);
            for (int i = 0; i < max; i++) {
                c = message.charAt(i);
                if (c != '\\') {
                    builder.append(c);
                } else {
                    i++;
                    c = message.charAt(i);
                    switch (c) {
                        case 'n':
                            builder.append('\n');
                            break;
                        case 'r':
                            builder.append('\r');
                            break;
                        default:
                            builder.append(c);
                            break;
                    }
                }
            }
            return builder.toString();
        }
    }

    //--

    public final LocalDateTime dateTime;
    public final String clientInvocation;
    public final String logger;
    public final String user;
    public final String stageName;
    public final String message;

    public LogEntry(LocalDateTime dateTime, String clientInvocation, String logger, String user, String stageName, String message) {
        this.dateTime = dateTime;
        this.clientInvocation = clientInvocation;
        this.logger = logger;
        this.user = user;
        this.stageName = stageName;
        this.message = message;
    }

    public String toString() {
        StringBuilder result;

        result = new StringBuilder();
        char c;

        result.append(LogEntry.DATE_FMT.format(LocalDateTime.now())).append('|');
        result.append(clientInvocation).append('|');
        result.append(logger).append('|');
        result.append(user).append('|');
        result.append(stageName).append('|');
        for (int i = 0, max = message.length(); i < max; i++) {
            c = message.charAt(i);
            switch (c) {
                case '\r':
                    result.append("\\r");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                default:
                    result.append(c);
                    break;
            }
        }
        result.append('\n');
        return result.toString();
    }
}
