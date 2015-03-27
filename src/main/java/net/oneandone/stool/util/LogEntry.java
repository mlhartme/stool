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

import net.oneandone.sushi.util.Separator;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.List;

public class LogEntry {
    private static final Separator SEP = Separator.on('|').trim();
    private static final DateTimeFormatter FMT = DateTimeFormat.forPattern("Y-M-d H:m:s,SSS");

    public static LogEntry parse(String line) {
        List<String> fields;

        fields = SEP.split(line);
        if (fields.size() != 7) {
            throw new IllegalArgumentException(line);
        }
        return new LogEntry(DateTime.parse(fields.get(0), FMT), fields.get(1), fields.get(2), fields.get(3), fields.get(4), fields.get(5), fields.get(6));
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
}
