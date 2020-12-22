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
package net.oneandone.stool.core;

import net.oneandone.stool.client.Caller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class HistoryEntry {
    public static HistoryEntry create(Caller caller) {
        Instant instant;
        LocalDateTime date;

        instant = Instant.ofEpochMilli(System.currentTimeMillis());
        date = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        return new HistoryEntry(date, caller.invocation, caller.user, caller.command);
    }

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss,SSS");

    /** Count-part of the Logging.log method. */
    public static HistoryEntry parse(String line) {
        int date;
        int invocation;
        int user;

        date = line.indexOf('|');
        invocation = line.indexOf('|', date + 1); // invocation id
        user = line.indexOf('|', invocation + 1);
        return new HistoryEntry(
                LocalDateTime.parse(line.substring(0, date), HistoryEntry.DATE_FMT),
                line.substring(date + 1, invocation),
                line.substring(invocation + 1, user),
                line.substring(user + 1));
    }

    //--

    public final LocalDateTime dateTime;
    public final String invocation;
    public final String user;
    public final String command;

    public HistoryEntry(LocalDateTime dateTime, String tnvocation, String user, String command) {
        this.dateTime = dateTime;
        this.invocation = tnvocation;
        this.user = user;
        this.command = command;
    }

    public String toString() {
        StringBuilder result;

        result = new StringBuilder();

        result.append(HistoryEntry.DATE_FMT.format(LocalDateTime.now())).append('|');
        result.append(invocation).append('|');
        result.append(user).append('|');
        result.append(command);
        return result.toString();
    }
}
