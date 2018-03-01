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
package net.oneandone.stool.util;

import java.util.Map;

/** A computable value representing one aspect of the stage status. */
public class TemplateProperty extends Property {
    private final Map<String, String> map;
    private final String key;

    public TemplateProperty(String name, Map<String, String> map, String key) {
        super(name);
        this.map = map;
        this.key = key;
    }

    public String get() {
        return map.get(key);
    }

    public void set(String str) {
        map.put(key, str);
    }
}
