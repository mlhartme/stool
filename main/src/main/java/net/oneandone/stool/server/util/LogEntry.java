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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class LogEntry {
    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss,SSS");
    public static final DateTimeFormatter FULL_FMT = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss");

    /** Count-part of the Logging.log method. */
    public static LogEntry parse(String line) {
        LocalTime timeObj;
        LocalDate dateObj;
        int len;
        int time;
        int requestId;
        String nameStr;
        int logger;
        int user;
        int stageId;

        len = line.length();

        // CAUTION: do not use split, because messages may contain separators
        time = line.indexOf('|');
        requestId = line.indexOf('|', time + 1); // invocation id
        logger = line.indexOf('|', requestId + 1);
        user = line.indexOf('|', logger + 1);
        stageId = line.indexOf('|', user + 1);
        if (line.charAt(len - 1) != '\n') {
            throw new IllegalArgumentException(line);
        }

        // TODO: doesn't work for commands running during midnight ...
        timeObj = LocalTime.parse(line.substring(0, time), TIME_FMT);
        nameStr = line.substring(time + 1, requestId);
        dateObj = LocalDate.parse(nameStr.substring(0, nameStr.indexOf(".")), Logging.DATE_FORMAT);
        return new LogEntry(LocalDateTime.of(dateObj, timeObj), nameStr,
                line.substring(requestId + 1, logger),
                line.substring(logger + 1, user),
                line.substring(user + 1, stageId),
                unescape(line.substring(stageId + 1, len -1)));
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
    public final String requestId;
    public final String logger;
    public final String user;
    public final String stageName;
    public final String message;

    public LogEntry(LocalDateTime dateTime, String requestId, String logger, String user, String stageName, String message) {
        this.dateTime = dateTime;
        this.requestId = requestId;
        this.logger = logger;
        this.user = user;
        this.stageName = stageName;
        this.message = message;
    }

    public String toString() {
        return message;
    }
}
