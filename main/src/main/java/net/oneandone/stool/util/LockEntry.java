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
package net.oneandone.stool.util;

import java.util.ArrayList;
import java.util.List;

public class LockEntry {
    /** Name of the lock */
    private final String name;

    /** >= 0 */
    private int exclusiveCount;

    /** undefined if count is 0 */
    private int exclusiveProcess;

    private final List<Integer> shared;

    public LockEntry(String name) {
        this.name = name;
        this.exclusiveCount = 0;
        this.exclusiveProcess = 0;
        this.shared = new ArrayList<>();
    }

    public boolean is(String cmp) {
        return name.equals(cmp);
    }

    public boolean tryLock(boolean exclusive, int process) {
        if (exclusive) {
            if (singleProcess(process)) {
                exclusiveProcess = process; // if count is 0
                exclusiveCount++;
                return true;
            } else {
                return false;
            }
        } else {
            if (exclusiveCount == 0 || singleProcess(process)) {
                shared.add(process);
                return true;
            } else {
                return false;
            }
        }
    }

    public void release(boolean exclusive, int process) {
        if (exclusive) {
            if (exclusiveProcess != process) {
                throw new IllegalStateException();
            }
            if (exclusiveCount <= 0) {
                throw new IllegalStateException();
            }
            exclusiveCount--;
        } else {
            for (int i = shared.size() - 1; i >= 0; i--) {
                if (process == shared.get(i)) {
                    shared.remove(i);
                    return;
                }
            }
            throw new IllegalStateException();
        }
    }

    private boolean singleProcess(int process) {
        if (exclusiveCount > 0 && process != exclusiveProcess) {
            return false;
        }
        for (Integer s : shared) {
            if (process != s) {
                return false;
            }
        }
        return true;
    }

    //--

    public String toLine() {
        StringBuilder result;

        if (exclusiveCount == 0 && shared.size() == 0) {
            return null;
        }
        result = new StringBuilder();
        result.append(name);
        if (exclusiveCount > 0) {
            result.append(' ');
            result.append(exclusiveCount);
            result.append(':');
            result.append(exclusiveProcess);
        }
        for (int s : shared) {
            result.append(' ');
            result.append(s);
        }
        return result.toString();
    }

    public static LockEntry parse(String line) {
        int idx;
        int prev;
        LockEntry result;
        int length;

        idx = line.indexOf(' ');
        if (idx == -1) {
            throw new IllegalArgumentException(line);
        }
        result = new LockEntry(line.substring(0, idx));
        prev = idx + 1;
        length = line.length();
        while (prev < length) {
            idx = line.indexOf(' ', prev);
            if (idx == -1) {
                result.addItem(line.substring(prev));
                break;
            } else {
                result.addItem(line.substring(prev, idx));
                prev = idx + 1;
            }
        }
        return result;
    }

    private void addItem(String item) {
        int idx;

        idx = item.indexOf(':');
        if (idx != -1) {
            exclusiveCount = number(item.substring(0, idx));
            exclusiveProcess = number(item.substring(idx + 1));
        } else {
            shared.add(number(item));
        }
    }

    private static int number(String str) {
        return Integer.parseInt(str);
    }
}
