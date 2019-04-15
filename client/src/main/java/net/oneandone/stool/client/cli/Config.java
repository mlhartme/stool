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
package net.oneandone.stool.client.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.client.Reference;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.util.Strings;

import java.util.LinkedHashMap;
import java.util.Map;

public class Config extends StageCommand {
    private final Map<String, String> arguments;

    private boolean get;
    private boolean set;

    public Config(Globals globals, World world, Console console) {
        super(globals, world, console);
        arguments = new LinkedHashMap<>();
    }

    public void property(String str) {
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
        if (arguments.containsKey(key)) {
            throw new ArgumentException("duplicate property: " + key);
        }
        if (get && set) {
            throw new ArgumentException("cannot mix get and set arguments");
        }
        if (arguments.put(key, value) != null) {
            throw new ArgumentException("duplicate property: " + key);
        }
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        Map<String, String> props;
        int width;

        if (set) {
            for (Map.Entry<String, String> entry : reference.client.setProperties(reference.stage, arguments).entrySet()) {
                console.info.println(entry.getKey() + "=" + entry.getValue());
            }
        } else {
            props = reference.client.getProperties(reference.stage);
            if (get) {
                props = selectedProperties(props);
            } else {
                // neither get nor set -> show all
            }
            width = 0;
            if (props.size() > 1) {
                for (String name : props.keySet()) {
                    width = Math.max(width, name.length());
                }
                width += 3;
            }
            for (Map.Entry<String, String> entry : props.entrySet()) {
                console.info.println(Strings.padLeft(entry.getKey(), width) + " : " + entry.getValue());
            }
        }
    }

    private Map<String, String> selectedProperties(Map<String, String> all) {
        Map<String, String> result;
        String property;

        result = new LinkedHashMap<>();
        for (String name : arguments.keySet()) {
            property = all.get(name);
            if (property == null) {
                throw new ArgumentException("unknown property: " + name);
            }
            result.put(name, property);
        }
        return result;
    }
}
