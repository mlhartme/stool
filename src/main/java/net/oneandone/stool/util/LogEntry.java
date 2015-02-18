/**
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
package net.oneandone.stool.util;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.util.UUID;

public class LogEntry {
    public final UUID uuid;
    public final String in;
    public final String out;
    public final String user;
    public final DateTime dateTime;
    public final String command;

    public LogEntry(UUID uuid, String in, String out, String user, DateTime dateTime, String command) {
        this.uuid = uuid;
        this.in = in;
        this.out = out;
        this.user = user;
        this.dateTime = dateTime;
        this.command = command;
    }

    public static LogEntry parse(String logLine) {
        DateTime dateTime;
        UUID uuid;
        String user;
        String[] log;
        String out;
        String command;

        log = logLine.split("\\|");
        dateTime = DateTime.parse(log[0].trim(), DateTimeFormat.forPattern("Y-M-d H:m:s,SSS"));
        uuid = UUID.fromString(log[1].substring(log[1].indexOf('=') + 1).trim());
        user = log[3].trim();
        switch (log[2].trim()) {
            case "OUT":
            case "ERR":
                out = log[4].trim();
                command = null;
                break;
            case "IN":
                out = null;
                command = log[4].trim();
                break;
            default:
                out = null;
                command = log[4].trim();
        }
        return new LogEntry(uuid, null, out, user, dateTime, command);
    }
}
