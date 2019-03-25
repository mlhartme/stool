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
package net.oneandone.stool.server.util;

import net.oneandone.sushi.util.Separator;

import java.util.ArrayList;
import java.util.List;

public class MultiString {
    private static final Separator SEP = Separator.on('|').trim();

    public final List<String> lst;

    public MultiString() {
        lst = new ArrayList<>();
        lst.add("");
    }

    public void append(String str) {
        int prev;
        int open;
        int close;
        List<String> tmp;

        prev = 0;
        while (true) {
            open = str.indexOf('(', prev);
            if (open == -1) {
                appendAll(str.substring(prev, str.length()));
                return;
            }
            open++;
            close = str.indexOf(')', open);
            if (close == -1) {
                throw new IllegalArgumentException("closing ) not found: " + str);
            }
            tmp = new ArrayList<>(lst);
            lst.clear();
            for (String p : SEP.split(str.substring(open, close))) {
                for (String a : tmp) {
                    lst.add(a + p);
                }
            }
            prev = close + 1;
        }
    }

    private void appendAll(String str) {
        for (int i = 0; i < lst.size(); i++) {
            lst.set(i, lst.get(i) + str);
        }
    }

    public boolean contains(String str) {
        return lst.contains(str);
    }
}
