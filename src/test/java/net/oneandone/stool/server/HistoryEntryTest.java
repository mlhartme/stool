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
package net.oneandone.stool.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HistoryEntryTest {
    @Test
    public void normal() {
        HistoryEntry entry;

        entry = HistoryEntry.parse("19-04-30 10:12:12,948|someUUID|cmd|mabraun|stageName|GET uri|404\n");
        assertEquals("2019-04-30T10:12:12.948", entry.dateTime.toString());
        assertEquals("someUUID", entry.clientInvocation);
        assertEquals("mabraun", entry.user);
        assertEquals("cmd", entry.clientCommand);
        assertEquals("stageName", entry.stageName);
        assertEquals("GET uri", entry.request);
        assertEquals(404, entry.status);
    }
}
