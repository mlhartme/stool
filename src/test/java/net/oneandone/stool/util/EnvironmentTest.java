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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EnvironmentTest {
    private final Environment env;

    public EnvironmentTest() {
        env = new Environment();
        env.set("A_B", "foo");
        env.set("x", "1");
    }

    @Test
    public void substitute() {
        subst("", "");
        subst("abc", "abc");
        subst("ab$c", "ab$$c");
        subst("1", "$x");
        subst("-1-", "-$x-");
        subst("1foo", "$x$A_B");
    }

    private void subst(String expected, String str) {
        assertEquals(expected, env.substitute(str));
    }
}
