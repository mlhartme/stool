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
package net.oneandone.stool.cli.command;


import net.oneandone.stool.cli.Reference;

import java.util.HashMap;
import java.util.Map;

public class CompoundResult extends Exception {
    public final Map<Reference, Exception> results;

    public CompoundResult() {
        results = new HashMap<>();
    }

    public int size() {
        return results.size();
    }

    public void success(Reference reference) {
        results.put(reference, null);
    }

    public void failure(Reference reference, Exception cause) {
        results.put(reference, cause);
        addSuppressed(cause);
    }

    /** @return null when there's no message */
    @Override
    public String getMessage() {
        StringBuilder result;

        if (getSuppressed().length == 0) {
            return null;
        } else {
            result = new StringBuilder("stage command failed for the following stage(s):\n");
            for (Map.Entry<Reference, Exception> entry : results.entrySet()) {
                if (entry.getValue() != null) {
                    result.append("  ").append(entry.getKey()).append(": ").append(entry.getValue().getMessage()).append('\n');
                }
            }
            return result.toString();
        }
    }
}
