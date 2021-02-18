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

import java.util.Comparator;

public final class Versions {
    public static final Comparator<String> CMP = (left, right) -> compare(left, right);

    /** @return &lt; 0 if left is before right */
    public static int compare(String leftAll, String rightAll) {
        String leftHead;
        String leftTail;
        String rightHead;
        String rightTail;
        Integer leftNum;
        Integer rightNum;
        int idx;
        int headCmp;

        if (leftAll.equals(rightAll)) {
            return 0; // end of recursion
        }

        idx = nextSeparator(leftAll);
        if (idx < 0) {
            leftHead = leftAll;
            leftTail = "";
        } else {
            leftHead = leftAll.substring(0, idx);
            leftTail = leftAll.substring(idx + 1);
        }
        idx = nextSeparator(rightAll);
        if (idx < 0) {
            rightHead = rightAll;
            rightTail = "";
        } else {
            rightHead = rightAll.substring(0, idx);
            rightTail = rightAll.substring(idx + 1);
        }
        leftNum = parseOpt(leftHead);
        rightNum = parseOpt(rightHead);
        if (leftNum != null && rightNum != null) {
            headCmp = leftNum.compareTo(rightNum);
        } else {
            headCmp = leftHead.compareTo(rightHead);
        }
        if (headCmp != 0) {
            return headCmp;
        } else {
            return compare(leftTail, rightTail);
        }
    }

    private static int nextSeparator(String str) {
        char c;

        for (int i = 0; i < str.length(); i++) {
            c = str.charAt(i);
            if (c == '.' || c == '-' || c == '_') {
                return i;
            }
        }
        return -1;
    }

    private static Integer parseOpt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Versions() {}
}
