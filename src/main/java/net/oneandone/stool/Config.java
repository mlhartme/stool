/**
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
package net.oneandone.stool;

import net.oneandone.stool.configuration.Property;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Remaining;

import javax.naming.NoPermissionException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Config extends StageCommand {
    private final Map<String, Property> all;
    private Map<Property, String> selected;

    private boolean get;
    private boolean set;

    public Config(Session session) throws IOException {
        super(session);
        all = StageConfiguration.properties(session.extensionsFactory);
        selected = new HashMap<>();
    }

    @Remaining
    public void property(String str) throws NoPermissionException {
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
        if (selected.containsKey(key)) {
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
    public void doInvoke(Stage stage) throws Exception {
        StageConfiguration configuration;
        boolean error;

        configuration = stage.config();
        if (set) {
            stage.checkOwnership();
            error = false;
            for (Map.Entry<Property, String> entry : selected.entrySet()) {
                try {
                    entry.getKey().set(configuration, entry.getValue());
                    console.info.println(entry.getKey().name + "=" + entry.getValue());
                } catch (Exception e) {
                    console.info.println("invalid value for property " + entry.getKey().name + " : " + e.getMessage());
                    e.printStackTrace(console.verbose);
                    error = true;
                }
            }
            if (!error) {
                session.saveStageProperties(configuration, stage.wrapper);
                if (session.isSelected(stage)) {
                    session.environment.setAll(session.environment(stage));
                }
            }
        } else if (get) {
            for (Property property : selected.keySet()) {
                console.info.println(property.name + "=" + property.get(configuration));
            }
        } else {
            for (Property property : all.values()) {
                console.info.println("# " + property.description);
                console.info.println(property.name + "=" + property.get(configuration));
                console.info.println();
            }
        }
    }

}
