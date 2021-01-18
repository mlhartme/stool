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
import net.oneandone.sushi.util.Strings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpressionsTest {
    private static final World world = World.createMinimal();

    private static Configuration configuration() {
        try {
            return Configuration.create(world); // TODO
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Expressions expressions() {
        return new Expressions(world, configuration(), "fqdn");
    }

    @Test
    public void normal() throws IOException {
        Expressions e;

        e = expressions();
        assertEquals("", e.eval(""));
        assertEquals("hello", e.eval("hello"));
        assertEquals("0", e.eval("${defaultExpire}"));
    }

    @Test
    public void clazz() throws IOException {
        Expressions e;
        Clazz clazz;
        Map<String, String> values;

        clazz = Clazz.forTest("name", "one", "1", "two", "${'2' + value('one')}");
        e = expressions();
        values = e.eval(clazz);
        assertEquals(Strings.toMap("one", "1", "two", "21"), values);
    }
}
