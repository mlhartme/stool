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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.Reference;
import net.oneandone.sushi.util.Strings;

import java.util.LinkedHashMap;
import java.util.Map;

public class Config extends IteratedStageCommand {
    private final Map<String, String> values;

    private boolean get;
    private boolean set;

    public Config(Globals globals) {
        super(globals);
        values = new LinkedHashMap<>();
    }

    public void value(String str) {
        int idx;
        String key;
        String value;

        idx = str.indexOf('=');
        if (idx == -1) {
            key = str;
            value = null;
            get = true;
        } else {
            key = str.substring(0, idx);
            value = str.substring(idx + 1);
            set = true;
        }
        if (values.containsKey(key)) {
            throw new ArgumentException("duplicate value: " + key);
        }
        if (get && set) {
            throw new ArgumentException("cannot mix get and set arguments");
        }
        if (values.put(key, value) != null) {
            throw new ArgumentException("value property: " + key);
        }
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        Map<String, String> loaded;
        int width;

        if (set) {
            for (Map.Entry<String, String> entry : reference.client.setValues(reference.stage, values).entrySet()) {
                console.info.println(entry.getKey() + "=" + entry.getValue());
            }
        } else {
            loaded = reference.client.getValues(reference.stage);
            if (get) {
                loaded = selectedValues(loaded);
            } else {
                // neither get nor set -> show all
            }
            width = 0;
            if (loaded.size() > 1) {
                for (String name : loaded.keySet()) {
                    width = Math.max(width, name.length());
                }
                width += 3;
            }
            for (Map.Entry<String, String> entry : loaded.entrySet()) {
                console.info.println(Strings.padLeft(entry.getKey(), width) + " : " + entry.getValue());
            }
        }
    }

    private Map<String, String> selectedValues(Map<String, String> all) {
        Map<String, String> result;
        String value;

        result = new LinkedHashMap<>();
        for (String name : values.keySet()) {
            value = all.get(name);
            if (value == null) {
                throw new ArgumentException("unknown value: " + name);
            }
            result.put(name, value);
        }
        return result;
    }
}
