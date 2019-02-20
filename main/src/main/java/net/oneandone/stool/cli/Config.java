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
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.util.Strings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Config extends StageCommand {
    private final Map<String, String> arguments;

    private boolean get;
    private boolean set;

    public Config(Session session) {
        super(session, Mode.NONE, Mode.EXCLUSIVE);
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
    public void doMain(Stage stage) throws Exception {
        boolean error;
        Property prop;
        String value;
        Collection<Property> props;
        int width;

        if (set) {
            error = false;
            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                prop = stage.propertyOpt(entry.getKey());
                if (prop == null) {
                    throw new ArgumentException("unknown property: " + entry.getKey());
                }
                value = entry.getValue();
                value = value.replace("{}", prop.get());
                try {
                    prop.set(value);
                    // don't print the value - e.g. expire translates numbers into dates
                    console.info.println(prop.name() + "=" + prop.get());
                } catch (RuntimeException e) {
                    console.info.println("invalid value for property " + prop.name() + " : " + e.getMessage());
                    e.printStackTrace(console.verbose);
                    error = true;
                }
            }
            if (!error) {
                stage.saveConfig();
            }
        } else {
            props = get ? argumentProperties(stage) : stage.properties();
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

    private List<Property> argumentProperties(Stage stage) {
        List<Property> result;
        Property property;

        result = new ArrayList<>();
        for (String name : arguments.keySet()) {
            property = stage.propertyOpt(name);
            if (property == null) {
                throw new ArgumentException("unknown property: " + name);
            }
            result.add(property);
        }
        return result;
    }
}
