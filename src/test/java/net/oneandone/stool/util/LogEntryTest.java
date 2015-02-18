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

import static org.junit.Assert.assertEquals;


public class LogEntryTest {
    @Test
    public void testLogEntry() throws Exception {
        String log;
        LogEntry entry;

        entry = LogEntry.parse("2013-11-28 10:12:12,948 | 32 | net.oneandone.sushi.cli.Command | mabraun | Invoking Stop");
        assertEquals(DateTime.parse("2013-11-28 10:12:12,948", DateTimeFormat.forPattern("Y-M-d h:m:s,SSS")), entry.dateTime);
        assertEquals("32", entry.id);
        assertEquals("mabraun", entry.user);
        assertEquals("net.oneandone.sushi.cli.Command", entry.logger);
        assertEquals("Invoking Stop", entry.message);

        log = "2013-11-28 10:12:11,632 | 19 | OUT | mabraun | Applications available:";
        entry = LogEntry.parse(log);
        assertEquals("OUT", entry.logger);
        assertEquals("Applications available:", entry.message);

        log = "2013-11-28 10:12:17,055 | 2 | ERR | mabraun | tomcat is no running.";
        entry = LogEntry.parse(log);
        assertEquals("ERR", entry.logger);
        assertEquals("tomcat is no running.", entry.message);

        log = "2013-11-28 10:12:17,055 | 9 | IN | mabraun | tomcat is no running.";
        entry = LogEntry.parse(log);
        assertEquals("IN", entry.logger);
        assertEquals("tomcat is no running.", entry.message);
    }


}
