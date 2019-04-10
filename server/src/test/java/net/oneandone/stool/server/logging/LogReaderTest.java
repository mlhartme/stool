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

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LogReaderTest {
    @Test
    public void testReadLog() throws Exception {
        World world;
        LogReader reader;
        int count;
        AccessLogEntry entry;

        world = World.create();
        reader = LogReader.create((FileNode) world.resource("logs"));
        count = 0;
        while (true) {
            entry = reader.prev();
            if (entry == null) {
                break;
            }
            count++;
        }
        assertEquals(20, count);
    }
}
