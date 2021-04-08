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
import java.util.Set;

public final class Diff {
    // list of key, leftValue, rightValue for all diffeering keys
    public static Diff diff(Map<String, String> left, Map<String, String> right) {
        Set<String> all;
        Diff result;
        String l;
        String r;

        all = new LinkedHashSet<>(left.keySet());
        all.addAll(right.keySet());
        result = new Diff();
        for (String key : all) {
            l = left.get(key);
            r = right.get(key);
            if (l == null || !l.equals(r)) {
                result.map.put(key, new Pair(l, r));
            }
        }
        return result;
    }

    public static Diff fromList(List<String> list) {
        Diff result;

        if (list.size() % 2 != 0) {
            throw new IllegalArgumentException(list.toString());
        }
        result = new Diff();
        for (int i = 0, max = list.size(); i < max; i += 2) {
            result.map.put(list.get(i), Pair.decode(list.get(i + 1)));
        }
        return result;
    }

    //--

    private final Map<String, Pair> map;

    public Diff() {
        map = new LinkedHashMap<>();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Diff withoutKeys(List<String> keys) {
        Diff result;

        result = new Diff();
        for (Map.Entry<String, Pair> entry : map.entrySet()) {
            if (!keys.contains(entry.getKey())) {
                result.map.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public boolean remove(String key) {
        return map.remove(key) != null;
    }

    public List<String> toList() {
        List<String> result;

        result = new ArrayList<>();
        for (Map.Entry<String, Pair> entry : map.entrySet()) {
            result.add(entry.getKey());
            result.add(entry.getValue().encode());
        }
        return result;
    }

    public int hashCode() {
        return map.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof Diff d) {
            return map.equals(d.map);
        } else {
            return false;
        }
    }

    public String toString() {
        StringBuilder result;
        Pair pair;

        result = new StringBuilder();
        for (Map.Entry<String, Pair> entry : map.entrySet()) {
            pair = entry.getValue();
            if (pair.left == null) {
                result.append("+ " + entry.getKey() + "=" + pair.right + "\n");
            } else if (pair.right == null) {
                result.append("- " + entry.getKey() + "=" + pair.left + "\n");
            } else {
                result.append("- " + entry.getKey() + "=" + pair.left + "\n");
                result.append("+ " + entry.getKey() + "=" + pair.right + "\n");
            }
        }
        return result.toString();
    }
}
