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
package net.oneandone.stool;

import net.oneandone.stool.cli.Select;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SelectTest {
    @Test
    public void test() {
        check(lst(), lst(), "foo");
        check(lst("abc"), lst("abc"), "abc");
        check(lst("xyz"), lst("abc", "xyz"), "XYz");
        check(lst("abc1", "abc2"), lst("abc1", "abc2"), "bc");
    }

    private void check(List<String> expected, List<String> names, String search) {
        assertEquals(expected, Select.candidates(names, search));
    }

    private static List<String> lst(String ... elements) {
        List<String> result;

        result = new ArrayList<>(elements.length);
        Collections.addAll(result, elements);
        return result;
    }
}
