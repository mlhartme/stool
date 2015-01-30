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
package net.oneandone.sales.tools.stool.util;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


public class LogEntryTest {
    @Test
    public void testLogEntry() throws Exception {
        String log;
        log = "2013-11-28 10:12:12,948 | UUID=0ef6b913-1a9f-4d80-8edd-aa18780b490a, stage=ac1-hello1 "
          + "| net.oneandone.sushi.cli.Command | mabraun | Invoking Stop";
        LogEntry logEntry = LogEntry.get(log);
        assertEquals(DateTime.parse("2013-11-28 10:12:12,948", DateTimeFormat.forPattern("Y-M-d h:m:s,SSS")),
          logEntry.dateTime);
        assertEquals(UUID.fromString("0ef6b913-1a9f-4d80-8edd-aa18780b490a"), logEntry.uuid);
        assertEquals("ac1-hello1", logEntry.stage);
        assertEquals("mabraun", logEntry.user);
        assertEquals("Invoking Stop", logEntry.command);
        assertNull(logEntry.in);
        assertNull(logEntry.out);


        log = "2013-11-28 10:12:11,632 | UUID=6153167c-dae5-4657-8c60-494f73faf0c1, stage=ac1-hello1 |"
          + " OUT.INFO | mabraun | Applications available:";
        logEntry = LogEntry.get(log);
        assertNull(logEntry.command);
        assertNull(logEntry.in);
        assertEquals("Applications available:", logEntry.out);

        log = "2013-11-28 10:12:17,055 | UUID=cdd52c15-6d51-4dbb-a8c1-ac095b50e101, stage=ac1-hello1 |"
          + "OUT.ERROR | mabraun | tomcat is no running.";
        logEntry = LogEntry.get(log);
        assertNull(logEntry.command);
        assertNull(logEntry.in);
        assertEquals("tomcat is no running.", logEntry.out);

        log = "2013-11-28 10:12:17,055 | UUID=cdd52c15-6d51-4dbb-a8c1-ac095b50e101, stage=ac1-hello1 |"
          + " IN | mabraun | tomcat is no running.";
        logEntry = LogEntry.get(log);
        assertNull(logEntry.in);
        assertNull(logEntry.out);
        assertEquals("tomcat is no running.", logEntry.command);
    }


}
