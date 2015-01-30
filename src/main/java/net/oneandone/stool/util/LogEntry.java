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
    public UUID uuid;
    public String in;
    public String out;
    public String user;
    public String stage;
    public DateTime dateTime;
    public String command;

    public static LogEntry get(String logLine) {
        LogEntry logEntry;
        String[] log;
        String[] mdc;

        logEntry = new LogEntry();

        log = logLine.split("\\|");
        mdc = log[1].split(", ");
        logEntry.dateTime = DateTime.parse(log[0].trim(), DateTimeFormat.forPattern("Y-M-d H:m:s,SSS"));
        logEntry.uuid = UUID.fromString(mdc[0].substring(mdc[0].indexOf('=') + 1));
        logEntry.stage = mdc[1].substring(mdc[1].indexOf('=') + 1).trim();
        logEntry.user = log[3].trim();
        switch (log[2].trim()) {
            case "OUT.INFO":
            case "OUT.ERROR":
                logEntry.out = log[4].trim();
                break;
            case "IN":
                logEntry.command = log[4].trim();
                break;
            default:
                logEntry.command = log[4].trim();
        }
        return logEntry;
    }

}
