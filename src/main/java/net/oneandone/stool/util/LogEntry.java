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
import org.joda.time.format.DateTimeFormatter;

public class LogEntry {
    private static final DateTimeFormatter FMT = DateTimeFormat.forPattern("Y-M-d H:m:s,SSS");

    public static LogEntry parse(String line) {
        int len;
        int date;
        int id;
        int logger;
        int user;
        int stageId;
        int stageName;

        len = line.length();

        // CAUTION: do not use split, because
        date = line.indexOf('|');
        id = line.indexOf('|', date + 1);
        logger = line.indexOf('|', id + 1);
        user = line.indexOf('|', logger + 1);
        stageId = line.indexOf('|', user + 1);
        stageName = line.indexOf('|', stageId + 1);
        if (stageName == -1) {
            throw new IllegalArgumentException(line);
        }
        if (line.charAt(len - 1) != '\n') {
            throw new IllegalArgumentException(line);
        }
        return new LogEntry(DateTime.parse(line.substring(0, date), FMT),
                line.substring(date + 1, id),
                line.substring(id + 1, logger),
                line.substring(logger + 1, user),
                line.substring(user + 1, stageId),
                line.substring(stageId + 1, stageName),
                line.substring(stageName + 1, len - 1));
    }

    //--

    public final DateTime dateTime;
    public final String id;
    public final String logger;
    public final String user;
    public final String stageId;
    public final String stageName;
    public final String message;

    public LogEntry(DateTime dateTime, String id, String logger, String user, String stageId, String stageName, String message) {
        this.dateTime = dateTime;
        this.id = id;
        this.logger = logger;
        this.user = user;
        this.stageId = stageId;
        this.stageName = stageName;
        this.message = message;
    }

    public String toString() {
        return message;
    }
}
