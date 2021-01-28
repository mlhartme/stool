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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Maps {
    // list of key, leftValue, rightValue for all diffeering keys
    public static List<String> diff(Map<String, String> left, Map<String, String> right) {
        Set<String> all;
        List<String> result;
        String l;
        String r;

        all = new LinkedHashSet<>(left.keySet());
        all.addAll(right.keySet());
        result = new ArrayList<>();
        for (String key : all) {
            l = left.get(key);
            r = right.get(key);
            if (l == null || !l.equals(r)) {
                result.add(key);
                result.add(l);
                result.add(r);
            }
        }
        return result;
    }

    private Maps() {
    }
}
