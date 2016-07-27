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

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class MultiStringTest {
    @Test
    public void append() {
        check("http://host/path", "http://host/path");
        check("(http|https)://h", "http://h", "https://h");
        check("(abc)1", "abc1");
        check("()1");
        check("(a|b|c)(1|2)", "a1", "b1", "c1", "a2", "b2", "c2");
    }

    public void check(String str, String ... mapped) {
        MultiString ms;

        ms = new MultiString();
        ms.append(str);
        assertEquals(Arrays.asList(mapped), ms.lst);
    }
}
