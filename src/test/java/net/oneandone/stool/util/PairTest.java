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
package net.oneandone.stool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PairTest {
    @Test
    public void tests() {
        check(null, null);
        check("a", null);
        check(null, "b");
        check("a", "b");
        check("abcd", "123");
    }

    public void check(String left, String right) {
        Pair p1, p2;
        String e;

        p1 = new Pair(left, right);
        e = p1.encode();
        p2 = Pair.decode(e);
        assertEquals(p1, p2);
        assertEquals(e, p2.encode());
    }
}
