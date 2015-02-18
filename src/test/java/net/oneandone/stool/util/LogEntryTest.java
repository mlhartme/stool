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
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


public class LogEntryTest {
    @Test
    public void testLogEntry() throws Exception {
        String log;
        LogEntry entry;

        log = "2013-11-28 10:12:12,948 | UUID=0ef6b913-1a9f-4d80-8edd-aa18780b490a "
          + "| net.oneandone.sushi.cli.Command | mabraun | Invoking Stop";
        entry = LogEntry.parse(log);
        assertEquals(DateTime.parse("2013-11-28 10:12:12,948", DateTimeFormat.forPattern("Y-M-d h:m:s,SSS")), entry.dateTime);
        assertEquals(UUID.fromString("0ef6b913-1a9f-4d80-8edd-aa18780b490a"), entry.uuid);
        assertEquals("mabraun", entry.user);
        assertEquals("net.oneandone.sushi.cli.Command", entry.logger);
        assertEquals("Invoking Stop", entry.message);


        log = "2013-11-28 10:12:11,632 | UUID=6153167c-dae5-4657-8c60-494f73faf0c1 |"
          + " OUT | mabraun | Applications available:";
        entry = LogEntry.parse(log);
        assertEquals("OUT", entry.logger);
        assertEquals("Applications available:", entry.message);

        log = "2013-11-28 10:12:17,055 | UUID=cdd52c15-6d51-4dbb-a8c1-ac095b50e101 |"
          + "ERR | mabraun | tomcat is no running.";
        entry = LogEntry.parse(log);
        assertEquals("ERR", entry.logger);
        assertEquals("tomcat is no running.", entry.message);

        log = "2013-11-28 10:12:17,055 | UUID=cdd52c15-6d51-4dbb-a8c1-ac095b50e101 |"
          + " IN | mabraun | tomcat is no running.";
        entry = LogEntry.parse(log);
        assertEquals("IN", entry.logger);
        assertEquals("tomcat is no running.", entry.message);
    }


}
