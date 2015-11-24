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

/** Process id with a comment. Internal class */
class Process {
    public final int id;
    private final String comment;

    public static Process parse(String line) {
        int idx;

        idx = line.indexOf(' ');
        if (idx == -1) {
            throw new IllegalArgumentException(line);
        }
        return new Process(Integer.parseInt(line.substring(0, idx)), line.substring(idx + 1));
    }

    public Process(int id, String comment) {
        this.id = id;
        this.comment = comment;
    }

    public String toLine() {
        return Integer.toString(id) + ' ' + comment;
    }

    public String toString() {
        return toLine();
    }
}
