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
package net.oneandone.stool.docker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BuildArgumentTest {
    @Test
    public void normal() {
        BuildArgument v;

        assertNull(BuildArgument.scan("# foo"));
        v = BuildArgument.scan("ARG a=7");
        assertEquals("a", v.name);
        assertEquals("7", v.dflt);
        v = BuildArgument.scan("ARG  b=false");
        assertEquals("b", v.name);
        assertEquals("false", v.dflt);
        v = BuildArgument.scan("arg b = true");
        assertEquals("b", v.name);
        assertEquals("true", v.dflt);
        v = BuildArgument.scan("Arg str");
        assertEquals("str", v.name);
        assertEquals("", v.dflt);
        v = BuildArgument.scan("ARG str= a b c");
        assertEquals("str", v.name);
        assertEquals("a b c", v.dflt);
    }
}
