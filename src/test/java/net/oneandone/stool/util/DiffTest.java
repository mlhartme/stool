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

public class DiffTest {
    @Test
    public void tests() {
        check("", Strings.toMap(), Strings.toMap());
        check("", Strings.toMap("a", "b"), Strings.toMap("a", "b"));
        check("- x=1\n+ x=2\n", Strings.toMap("a", "b", "x", "1"), Strings.toMap("a", "b", "x", "2"));
        check("- a=b\n", Strings.toMap("a", "b"), Strings.toMap());
        check("+ a=b\n", Strings.toMap(), Strings.toMap("a", "b"));
    }

    public void check(String diffStr, Map<String, String> left, Map<String, String> right) {
        Diff diff;
        List<String> lst;

        diff = Diff.diff(left, right);
        Assertions.assertEquals(diffStr, diff.toString());
        lst = diff.toList();
        Assertions.assertEquals(diff, Diff.fromList(lst));
    }
}
