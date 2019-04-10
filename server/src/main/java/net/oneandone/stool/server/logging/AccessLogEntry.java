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
package net.oneandone.stool.server.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class AccessLogEntry {
    public static AccessLogEntry forEvent(ILoggingEvent event) {
        Instant instant;
        LocalDateTime date;
        Map<String, String> mdc;

        instant = Instant.ofEpochMilli(event.getTimeStamp());
        date = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        mdc = event.getMDCPropertyMap();
        return new AccessLogEntry(date, mdc.get("client-invocation"), mdc.get("client-command"), mdc.get("user"), mdc.get("stage"), event.getMessage());
    }

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss,SSS");

    /** Count-part of the Logging.log method. */
    public static AccessLogEntry parse(String line) {
        int len;

        int date;
        int invocation;
        int command;
        int user;
        int stage;

        len = line.length();

        // CAUTION: do not use split, because messages may contain separators
        date = line.indexOf('|');
        invocation = line.indexOf('|', date + 1); // invocation id
        command = line.indexOf('|', invocation + 1);
        user = line.indexOf('|', command + 1);
        stage = line.indexOf('|', user + 1);
        if (line.charAt(len - 1) != '\n') {
            throw new IllegalArgumentException(line);
        }

        return new AccessLogEntry(
                LocalDateTime.parse(line.substring(0, date), AccessLogEntry.DATE_FMT),
                line.substring(date + 1, invocation),
                line.substring(invocation + 1, command),
                line.substring(command + 1, user),
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
    public final String clientCommand;
    public final String user;
    public final String stageName;
    public final String message;

    public AccessLogEntry(LocalDateTime dateTime, String clientInvocation, String clientCommand, String user, String stageName, String message) {
        this.dateTime = dateTime;
        this.clientInvocation = clientInvocation;
        this.clientCommand = clientCommand;
        this.user = user;
        this.stageName = stageName;
        this.message = message;
    }

    public String toString() {
        StringBuilder result;

        result = new StringBuilder();
        char c;

        result.append(AccessLogEntry.DATE_FMT.format(LocalDateTime.now())).append('|');
        result.append(clientInvocation).append('|');
        result.append(clientCommand).append('|');
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
