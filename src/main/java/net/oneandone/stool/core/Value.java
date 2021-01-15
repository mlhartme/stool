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
package net.oneandone.stool.core;

import net.oneandone.stool.helmclasses.ValueType;
import net.oneandone.stool.kubernetes.Engine;

/** A stored value representing one aspect of the stage status. */
public class Value extends Info {
    private final ValueType type;
    private final String value;

    public Value(String name, ValueType type, String value) {
        super(name);
        this.type = type;
        this.value = value;
    }

    public Value withNewValue(String str) {
        return new Value(name(), type, str.replace("{}", value));
    }

    public String disclose() {
        if (type.privt) {
            return "(private)";
        } else {
            return value;
        }
    }

    public String get(Engine engine) {
        return get();
    }

    public String get() {
        return value;
    }

    public String toString() {
        return name() + ": " + get();
    }
}
