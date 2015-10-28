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

public class Lock implements AutoCloseable {
    public enum Mode {
        NONE, SHARED, EXCLUSIVE
    }

    public final LockManager locks;
    public final String name;
    public final Mode mode;

    public Lock(LockManager locks, String name, Mode mode) {
        this.locks = locks;
        this.name = name;
        this.mode = mode;
    }

    @Override
    public void close() throws Exception {
        locks.release(name, mode);
    }

    public String toString() {
        return name + " (" + mode.toString().toLowerCase() + ")";
    }
}
