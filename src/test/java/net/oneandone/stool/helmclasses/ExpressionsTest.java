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

import freemarker.template.TemplateException;
import net.oneandone.sushi.fs.World;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpressionsTest {
    @Test
    public void normal() throws IOException, TemplateException {
        Expressions m;

        m = new Expressions(World.create(), null /* TODO */, "fqdn");
        assertEquals("", m.compute(""));
        assertEquals("hello", m.compute("hello"));
        assertEquals("42", m.compute("${test}"));
        assertEquals("", m.compute("${concat()}"));
        assertEquals("1", m.compute("${concat(1)}"));
        assertEquals("13", m.compute("${concat(1, 3)}"));
    }
}
