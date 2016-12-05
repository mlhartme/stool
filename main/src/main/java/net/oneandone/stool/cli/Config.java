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
import net.oneandone.stool.configuration.Property;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.util.Strings;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Config extends StageCommand {
    private final Map<String, Property> all;
    private final Map<Property, String> selected;

    private boolean get;
    private boolean set;

    public Config(Session session) {
        super(false, session, Mode.NONE, Mode.EXCLUSIVE, Mode.NONE);
        all = session.properties();
        selected = new HashMap<>();
    }

    public void property(String str) {
        int idx;
        String key;
        String value;
        Property property;

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
        try {
            property = all.get(key);
            if (property == null) {
                throw new ArgumentException("unknown property: " + key);
            }
        } catch (SecurityException e) {
            throw new ArgumentException(e.getMessage());
        }
        if (selected.containsKey(property)) {
            throw new ArgumentException("duplicate property: " + key);
        }
        if (get && set) {
            throw new ArgumentException("cannot mix get and set arguments");
        }
        if (selected.put(property, value) != null) {
            throw new ArgumentException("duplicate property: " + key);
        }
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        StageConfiguration configuration;
        boolean error;
        Property prop;
        String value;
        Collection<Property> props;
        int width;

        configuration = stage.config();
        if (set) {
            stage.modify();
            error = false;
            for (Map.Entry<Property, String> entry : selected.entrySet()) {
                prop = entry.getKey();
                value = entry.getValue();
                value = value.replace("{}", prop.get(configuration));
                try {
                    prop.set(configuration, value);
                    // don't print the value - e.g. expire translates numbers into dates
                    console.info.println(prop.name + "=" + prop.get(configuration));
                } catch (RuntimeException e) {
                    console.info.println("invalid value for property " + prop.name + " : " + e.getMessage());
                    e.printStackTrace(console.verbose);
                    error = true;
                }
            }
            if (!error) {
                session.saveStageProperties(configuration, stage.backstage);
            }
        } else {
            props = get ? selected.keySet() : all.values();
            width = 0 ;
            if (props.size() > 1) {
                for (Property property : props) {
                    width = Math.max(width, property.name.length());
                }
                width += 3;
            }
            for (Property property : props) {
                console.info.println(Strings.padLeft(property.name, width) + " : " + property.get(configuration));
            }
        }
    }

}
