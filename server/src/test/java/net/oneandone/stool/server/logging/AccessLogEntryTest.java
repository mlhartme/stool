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
package net.oneandone.stool.server.logging;

import net.oneandone.stool.server.logging.AccessLogEntry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AccessLogEntryTest {
    @Test
    public void normal() {
        AccessLogEntry entry;

        entry = AccessLogEntry.parse("19-04-30 10:12:12,948|someUUID|cmd|mabraun|stageName|message with | separator \n");
        assertEquals("2019-04-30T10:12:12.948", entry.dateTime.toString());
        assertEquals("someUUID", entry.clientInvocation);
        assertEquals("mabraun", entry.user);
        assertEquals("cmd", entry.clientCommand);
        assertEquals("stageName", entry.stageName);
        assertEquals("message with | separator ", entry.message);
    }
}
