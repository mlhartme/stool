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

import java.util.Map;

/** A stored value representing one aspect of the stage status. */
public class Value extends Info {
    public static Value create(String name, Map<String, Object> values, String dflt) {
        Object result;

        result = values.get(name);
        return new Value(name, result == null ? dflt : result.toString());
    }

    private final String value;

    public Value(String name, String value) {
        super(name);
        this.value = value;
    }

    public String get(Context context) {
        return get();
    }

    public String get() {
        return value;
    }

    public String toString() {
        return name() + ": " + get();
    }
}