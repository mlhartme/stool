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

import java.util.Map;

/** Substitute one-character variables. Sushi Substitution needs a none-empty end marker. */
public class Subst {
    public static String subst(String str, Map<Character, String> map) {
        int prev;
        int pos;
        StringBuilder builder;
        String s;
        char c;

        builder = new StringBuilder();
        prev = 0;
        while (true) {
            pos = str.indexOf('%', prev);
            if (pos == -1 || pos == str.length() - 1) {
                builder.append(str.substring(prev));
                return builder.toString();
            }
            builder.append(str.substring(prev, pos));
            c = str.charAt(pos + 1);
            s = map.get(c);
            if (s == null) {
                throw new IllegalArgumentException("unknown variable: %" + c);
            }
            builder.append(s);
            prev = pos + 2;
        }
    }

}
