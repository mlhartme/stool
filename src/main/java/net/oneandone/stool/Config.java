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

import net.oneandone.stool.configuration.Option;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.configuration.Until;
import net.oneandone.stool.extensions.ExtensionsFactory;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Role;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Remaining;

import javax.naming.NoPermissionException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Config extends StageCommand {
    private final Map<String, Property> all;
    private Map<Property, String> selected;

    private boolean get;
    private boolean set;

    public Config(Session session) throws IOException {
        super(session);
        all = getProperties(session.extensionsFactory);
        selected = new HashMap<>();
    }

    public static Map<String, Property> getProperties(ExtensionsFactory extensions) {
        Map<String, Property> result;
        Option option;

        result = new LinkedHashMap<>();
        for (Field field : StageConfiguration.class.getFields()) {
            option = field.getAnnotation(Option.class);
            if (option != null) {
                result.put(option.key(), new Property(option.key(), option.description(), field, null));
            }
        }
        extensions.fields(result);
        return result;
    }

    @Remaining
    public void property(String str) throws NoPermissionException {
        int idx;
        String key;
        String value;
        Role role;
        Property property;

        role = session.configuration.security.isWaterloo()? Role.detect(session.configuration) : Role.ADMIN;
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
            property.securityCheck(role);
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

    public static class Property {
        public final String name;
        public final String description;
        private final Field field;
        private final String extension;

        public Property(String name, String description, Field field, String extension) {
            this.name = name;
            this.description = description;
            this.field = field;
            this.extension = extension;
            field.setAccessible(true);
        }

        public Object get(StageConfiguration configuration) {
            try {
                return field.get(object(configuration));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        public void set(StageConfiguration configuration, Object strOrMap) throws NoSuchFieldException {
            Object value;
            Class type;
            String str;

            type = field.getType();
            if (type.equals(String.class)) {
                value = strOrMap;
            } else if (type.equals(Boolean.class) || type.equals(Boolean.TYPE)) {
                value = Boolean.valueOf((String) strOrMap);
            } else if (type.equals(Integer.class) || type.equals(Integer.TYPE)) {
                value = Integer.valueOf((String) strOrMap);
            } else if (type.equals(List.class)) {
                str = (String) strOrMap;
                if (str.contains(",")) {
                    value = Arrays.asList(str.split(","));
                } else if (str.contains(" ")) {
                    value = Arrays.asList(str.split(" "));
                } else if (str.length() > 0) {
                    ArrayList<String> list = new ArrayList<>();
                    list.add(str);
                    value = list;
                } else {
                    value = Collections.emptyList();
                }
            } else if (type.equals(Until.class)) {
                value = Until.fromHuman((String) strOrMap);
            } else if (Map.class.isAssignableFrom(type)) {
                value = strOrMap;
            } else {
                throw new IllegalStateException("Cannot convert String to " + type.getSimpleName());
            }
            try {
                field.set(object(configuration), value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        public void securityCheck(Role role) {
            // TODO
        }

        private Object object(StageConfiguration configuration) {
            Object object;

            if (extension == null) {
                object = configuration;
            } else {
                object = configuration.extensions.get(extension);
                if (object == null) {
                    throw new IllegalStateException("missing extension: " + extension);
                }
            }
            return object;
        }
    }
}
