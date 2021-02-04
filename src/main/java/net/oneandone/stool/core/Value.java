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

import net.oneandone.stool.helmclasses.Field;

/** Element of a class; to can get and set values. */
public class Value {
    public final Field field;
    private final String value;

    public Value(Field field, String value) {
        this.field = field;
        this.value = value;
    }

    public String name() {
        return field.name;
    }

    public Value withNewValue(String str) {
        return new Value(field, str.replace("{}", value));
    }

    public String get() {
        return value;
    }

    public String toString() {
        return name() + ": " + get();
    }
}
