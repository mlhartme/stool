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
public class Property extends Info {
    private final String dflt;
    private final Map<String, Object> values;

    public Property(String name, String dflt, Map<String, Object> values) {
        super(name);
        this.dflt = dflt;
        this.values = values;
    }

    public String get(Context context) {
        return get();
    }

    public String get() {
        Object result;

        result = values.get(name());
        return result == null ? dflt : result.toString();
    }

    public void set(String str) {
        values.put(name(), str);
    }

    public String toString() {
        return name() + ": " + get();
    }
}
