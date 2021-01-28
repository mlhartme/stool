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

import net.oneandone.sushi.util.Strings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PairTest {
    @Test
    public void empty() {
        check(Strings.toMap(), Strings.toMap());
        check(Strings.toMap("a", "b"), Strings.toMap("a", "b"));
        check(Strings.toMap("a", "b", "x", "1"), Strings.toMap("a", "b", "x", "2"), "x", "1", "2");
        check(Strings.toMap("a", "b"), Strings.toMap(), "a", "b", Pair.NIL);
        check(Strings.toMap(), Strings.toMap("a", "b"), "a", Pair.NIL, "b");
    }

    public void check(Map<String, String> left, Map<String, String> right, String... expected) {
        Map<String, Pair> diff;
        List<String> lst;

        diff = Pair.diff(left, right);
        lst = Pair.toList(diff);
        Assertions.assertEquals(Arrays.asList(expected), lst);
        Assertions.assertEquals(diff, Pair.fromList(lst));
    }
}
