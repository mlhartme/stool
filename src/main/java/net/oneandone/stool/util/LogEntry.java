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
    public final DateTime dateTime;
    public final UUID uuid;
    public final String logger;
    public final String user;
    public final String message;

    public LogEntry(DateTime dateTime, UUID uuid, String logger, String user, String message) {
        this.dateTime = dateTime;
        this.uuid = uuid;
        this.logger = logger;
        this.user = user;
        this.message = message;
    }

    public static LogEntry parse(String line) {
        DateTime dateTime;
        UUID uuid;
        String user;
        String[] log;
        String logger;
        String message;

        log = line.split("\\|");
        if (log.length != 5) {
            throw new IllegalArgumentException(line);
        }
        dateTime = DateTime.parse(log[0].trim(), DateTimeFormat.forPattern("Y-M-d H:m:s,SSS"));
        uuid = UUID.fromString(log[1].substring(log[1].indexOf('=') + 1).trim());
        logger = log[2].trim();
        user = log[3].trim();
        message = log[4].trim();
        return new LogEntry(dateTime, uuid, logger, user, message);
    }
}
