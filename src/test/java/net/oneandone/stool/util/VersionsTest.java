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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VersionsTest {

    @Test
    void compare() {
        eq("", "");
        eq("abc", "abc");

        eq("0", "0");
        lt("0", "1");
        gt("1", "0");

        gt("1", "0.a");
        lt("1.1", "1.2");

        lt("9.0.30", "9.0.31");
        lt("9.0.30", "9.1.0");
        lt("9.0.30", "10.0.0-beta");
        gt("9.0.31", "7.0.56");
    }

    private void eq(String left, String right) {
        check(0, left, right);
        check(0, right, left);
    }
    private void lt(String left, String right) {
        check(-1, left, right);
        check(1, right, left);
    }
    private void gt(String left, String right) {
        check(1, left, right);
        check(-1, right, left);
    }

    private void check(int expected, String left, String right) {
        int result;

        result = Versions.compare(left, right);
        if (expected == 0) {
            Assertions.assertEquals(0, result);
        } else if (expected > 0) {
            Assertions.assertTrue(result > 0);
        } else if (expected < 0) {
            Assertions.assertTrue(result < 0);
        } else {
            throw new IllegalStateException();
        }
    }
}
