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

package net.oneandone.stool.locking;

import java.util.ArrayList;
import java.util.List;

/** A lock with processes holding this lock. Internal class. */
class Queue {
    /** Name of the lock */
    public final String lock;

    /** >= 0 */
    public int exclusiveCount;

    /** undefined if count is 0 */
    public Process exclusiveProcess;

    public final List<Process> shared;

    public Queue(String lock) {
        this.lock = lock;
        this.exclusiveCount = 0;
        this.exclusiveProcess = null;
        this.shared = new ArrayList<>();
    }

    public boolean is(String cmp) {
        return lock.equals(cmp);
    }

    public boolean tryLock(boolean exclusive, Process process) {
        if (exclusive) {
            if (containsOther(process)) {
                return false;
            } else {
                if (exclusiveCount == 0) {
                    exclusiveProcess = process;
                }
                exclusiveCount++;
                return true;
            }
        } else {
            if (exclusiveCount == 0 || !containsOther(process)) {
                shared.add(process);
                return true;
            } else {
                return false;
            }
        }
    }

    public void release(boolean exclusive, Process process) {
        if (exclusive) {
            if (!process.equals(exclusiveProcess)) {
                throw new IllegalStateException();
            }
            if (exclusiveCount <= 0) {
                throw new IllegalStateException();
            }
            exclusiveCount--;
        } else {
            for (int i = shared.size() - 1; i >= 0; i--) {
                if (process.equals(shared.get(i))) {
                    shared.remove(i);
                    return;
                }
            }
            throw new IllegalStateException();
        }
    }

    public boolean contains(Process process) {
        if (exclusiveCount > 0 && process == exclusiveProcess) {
            return true;
        }
        for (Process p : shared) {
            if (p == process) {
                return true;
            }
        }
        return false;
    }

    public boolean containsOther(Process process) {
        if (exclusiveCount > 0 && process != exclusiveProcess) {
            return true;
        }
        for (Process s : shared) {
            if (process != s) {
                return true;
            }
        }
        return false;
    }
}
