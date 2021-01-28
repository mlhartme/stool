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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Pair {
    // list of key, leftValue, rightValue for all diffeering keys
    public static Map<String, Pair> diff(Map<String, String> left, Map<String, String> right) {
        Set<String> all;
        Map<String, Pair> result;
        String l;
        String r;

        all = new LinkedHashSet<>(left.keySet());
        all.addAll(right.keySet());
        result = new LinkedHashMap<>();
        for (String key : all) {
            l = left.get(key);
            r = right.get(key);
            if (l == null || !l.equals(r)) {
                result.put(key, new Pair(l, r));
            }
        }
        return result;
    }

    public static Map<String, Pair> fromList(List<String> list) {
        Map<String, Pair> result;

        if (list.size() % 3 != 0) {
            throw new IllegalArgumentException(list.toString());
        }
        result = new LinkedHashMap<>();
        for (int i = 0, max = list.size(); i < max; i += 3) {
            result.put(list.get(i), new Pair(decode(list.get(i + 1)), decode(list.get(i + 2))));
        }
        return result;
    }

    public static List<String> toList(Map<String, Pair> diff) {
        List<String> result;

        result = new ArrayList<>();
        for (Map.Entry<String, Pair> entry : diff.entrySet()) {
            result.add(entry.getKey());
            entry.getValue().toList(result);
        }
        return result;
    }

    //--

    public final String left;
    public final String right;

    public Pair(String left, String right) {
        this.left = left;
        this.right = right;
    }

    public void toList(List<String> result) {
        result.add(encode(left));
        result.add(encode(right));
    }

    public int hashCode() {
        return left == null ? right.hashCode() : left.hashCode();
    }

    public boolean equals(Object obj) {
        Pair p;

        if (obj instanceof Pair) {
            p = (Pair) obj;
            return Objects.equals(p.left, left) && Objects.equals(p.right, right);
        } else {
            return false;
        }
    }

    public static final String NIL = "__null__";

    private static String encode(String str) {
        return str == null ? NIL : str;
    }
    private static String decode(String str) {
        return NIL.equals(str) ? null : str;
    }
}
