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
package net.oneandone.stool.stage.artifact;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Changes implements Iterable<Change> {

    private final List<Change> changes;
    private boolean exception;

    public Changes() {
        this.changes = new ArrayList<>();
    }

    public void add(Change change) {
        changes.add(change);
    }

    public void addAll(Changes others) {
        changes.addAll(others.changes);
        if (others.isException()) {
            setException(true);
        }
    }

    public boolean isException() {
        return exception;
    }

    public void setException(boolean exception) {
        this.exception = exception;
    }

    public int size() {
        return changes.size();
    }

    @Override
    public Iterator<Change> iterator() {
        return changes.iterator();
    }

    @Override
    public String toString() {
        StringBuilder result;

        result = new StringBuilder();
        for (Change change : changes) {
            result.append(change.getUser()).append(": \n").append(change.getMessage()).append("\n\n");
        }
        return result.toString();
    }
}
