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
package net.oneandone.stool.server.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LogEntryTest {
    @Test
    public void normal() {
        LogEntry entry;

        entry = LogEntry.parse("10:12:12,948|131128.32|net.oneandone.sushi.cli.Command|mabraun|stageName|message with | separator \n");
        assertEquals("2013-11-28T10:12:12.948", entry.dateTime.toString());
        assertEquals("131128.32", entry.requestId);
        assertEquals("mabraun", entry.user);
        assertEquals("net.oneandone.sushi.cli.Command", entry.logger);
        assertEquals("stageName", entry.stageName);
        assertEquals("message with | separator ", entry.message);
    }
}
