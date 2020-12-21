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
package net.oneandone.stool.server;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class HistoryEntry {
    public static HistoryEntry create(String cmd) { // TODO
        Instant instant;
        LocalDateTime date;

        instant = Instant.ofEpochMilli(System.currentTimeMillis());
        date = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        return new HistoryEntry(date, "clientInvocation", cmd, "user", "stage", "request", 200);
    }

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss,SSS");

    /** Count-part of the Logging.log method. */
    public static HistoryEntry parse(String line) {
        int len;

        int date;
        int invocation;
        int command;
        int user;
        int stage;
        int request;

        len = line.length();

        // CAUTION: do not use split, because messages may contain separators
        date = line.indexOf('|');
        invocation = line.indexOf('|', date + 1); // invocation id
        command = line.indexOf('|', invocation + 1);
        user = line.indexOf('|', command + 1);
        stage = line.indexOf('|', user + 1);
        request = line.indexOf('|', stage + 1);
        if (request < 0) {
            throw new IllegalArgumentException(line);
        }
        if (line.charAt(len - 1) != '\n') {
            throw new IllegalArgumentException(line);
        }

        return new HistoryEntry(
                LocalDateTime.parse(line.substring(0, date), HistoryEntry.DATE_FMT),
                line.substring(date + 1, invocation),
                line.substring(invocation + 1, command),
                line.substring(command + 1, user),
                line.substring(user + 1, stage),
                line.substring(stage + 1, request),
                Integer.parseInt(line.substring(request + 1, len - 1)));
    }

    //--

    public final LocalDateTime dateTime;
    public final String clientInvocation;
    public final String clientCommand;
    public final String user;
    public final String stageName;
    public final String request;
    public final int status;

    public HistoryEntry(LocalDateTime dateTime, String clientInvocation, String clientCommand, String user, String stageName, String request, int status) {
        this.dateTime = dateTime;
        this.clientInvocation = clientInvocation;
        this.clientCommand = clientCommand;
        this.user = user;
        this.stageName = stageName;
        this.request = request;
        this.status = status;
    }

    public String toString() {
        StringBuilder result;

        result = new StringBuilder();
        char c;

        result.append(HistoryEntry.DATE_FMT.format(LocalDateTime.now())).append('|');
        result.append(clientInvocation).append('|');
        result.append(clientCommand).append('|');
        result.append(user).append('|');
        result.append(stageName).append('|');
        result.append(request).append('|');
        result.append(status);
        result.append('\n');
        return result.toString();
    }
}
