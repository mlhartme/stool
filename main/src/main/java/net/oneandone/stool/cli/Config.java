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
package net.oneandone.stool.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Server;
import net.oneandone.sushi.util.Strings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Config extends StageCommand {
    private final Map<String, String> arguments;

    private boolean get;
    private boolean set;

    public Config(Server server) {
        super(server);
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
        List<Property> props;
        int width;

        if (set) {
            for (Property prop : server.setProperties(reference, arguments)) {
                console.info.println(prop.name() + "=" + prop.get());
            }
        } else {
            props = server.getProperties(reference);
            if (get) {
                props = argumentProperties(props);
            }
            width = 0 ;
            if (props.size() > 1) {
                for (Property property : props) {
                    width = Math.max(width, property.name().length());
                }
                width += 3;
            }
            for (Property property : props) {
                console.info.println(Strings.padLeft(property.name(), width) + " : " + property.get());
            }
        }
    }

    private List<Property> argumentProperties(List<Property> all) {
        List<Property> result;
        Property property;

        result = new ArrayList<>();
        for (String name : arguments.keySet()) {
            property = lookup(all, name);
            if (property == null) {
                throw new ArgumentException("unknown property: " + name);
            }
            result.add(property);
        }
        return result;
    }

    private Property lookup(List<Property> properties, String name) {
        for (Property property : properties) {
            if (name.equals(property.name())) {
                return property;
            }
        }
        return null;
    }
}
