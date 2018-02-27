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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.configuration.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Field or Property */
public interface Info {
    static Info get(Map<String, Property> properties, String str) {
        Info result;
        List<String> lst;

        result = Field.valueOfOpt(str);
        if (result != null) {
            return result;
        }
        result = properties.get(str);
        if (result != null) {
            return result;
        }
        lst = new ArrayList<>();
        for (Field f : Field.values()) {
            lst.add(f.name);
        }
        lst.addAll(properties.keySet());
        throw new ArgumentException(str + ": no such status field or property, choose one of " + lst);
    }

    String infoName();
}
