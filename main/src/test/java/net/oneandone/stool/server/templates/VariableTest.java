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
package net.oneandone.stool.server.templates;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VariableTest {
    @Test
    public void normal() throws IOException {
        Variable v;

        assertNull(Variable.scan("# foo"));
        v = Variable.scan("ARG a=7");
        assertEquals("a", v.name);
        assertEquals("7", v.dflt);
        v = Variable.scan("ARG  b=false");
        assertEquals("b", v.name);
        assertEquals(false, v.dflt);
        v = Variable.scan("arg b = true");
        assertEquals("b", v.name);
        assertEquals(true, v.dflt);
        v = Variable.scan("Arg str");
        assertEquals("str", v.name);
        assertEquals("", v.dflt);
        v = Variable.scan("ARG str= a b c");
        assertEquals("str", v.name);
        assertEquals("a b c", v.dflt);
    }
}
