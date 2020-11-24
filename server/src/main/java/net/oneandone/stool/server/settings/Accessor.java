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
package net.oneandone.stool.server.settings;

/** Handles settings access. Converts between strings an objects and deals with reflection */
public abstract class Accessor {
    public final String name;

    public Accessor(String name) {
        this.name = name;
    }

    public String get(Settings settings) {
        return doGet(settings);
    }

    protected abstract String doGet(Object settings);

    public void set(Settings settings, String str) {
        doSet(settings, str);
    }

    protected abstract void doSet(Object settings, String str);

    public int hashCode() {
        return name.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof Accessor) {
            return name.equals(((Accessor) obj).name);
        }
        return false;
    }
}
