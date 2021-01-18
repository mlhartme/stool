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
package net.oneandone.stool.helmclasses;

import net.oneandone.stool.core.Configuration;
import net.oneandone.sushi.fs.World;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpressionsTest {
    @Test
    public void normal() throws IOException {
        World world;
        Configuration c;
        Expressions m;

        world = World.create();
        c = Configuration.create(world); // TODO
        m = new Expressions(world, c, "fqdn");
        assertEquals("", m.eval(""));
        assertEquals("hello", m.eval("hello"));
        assertEquals("0", m.eval("${defaultExpire}"));
    }
}
