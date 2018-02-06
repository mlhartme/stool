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
package net.oneandone.stool.templates;

import net.oneandone.stool.configuration.Property;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class TemplatesFactory {
    public static TemplatesFactory create(World world) {
        TemplatesFactory factory;
        Properties properties;

        try {
            factory = new TemplatesFactory();
            for (Node node : world.resources("META-INF/stool-templates.properties")) {
                properties = node.readProperties();
                for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                    try {
                        factory.put((String) entry.getKey(), (Class) Class.forName((String) entry.getValue()));
                    } catch (ClassNotFoundException e) {
                        throw new IllegalArgumentException("template not found: " + entry.getValue());
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("unexpected io exception", e);
        }
        return factory;
    }

    //--

    private final Map<String, Class<? extends Template>> types;

    public TemplatesFactory() {
        types = new HashMap<>();
    }

    public void put(String name, Class<? extends Template> template) {
        if (types.put(name, template) != null) {
            throw new IllegalArgumentException("duplicate template: " + name);
        }
    }

    public Collection<String> typeNames() {
        return types.keySet();
    }

    public Class<? extends Template> type(String name) {
        return types.get(name);
    }

    public Template typeInstantiate(String name) {
        try {
            return types.get(name).newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("cannot instantiate template " + name + ": " + e.getMessage(), e);
        }
    }

    public Templates newInstance() {
        Templates templates;

        templates = new Templates();
        for (String name: types.keySet()) {
            templates.add(name, false, typeInstantiate(name));
        }
        return templates;
    }

    public void fields(Map<String, Property> result) {
        String name;
        String fullName;
        Class<? extends Template> template;
        int modifiers;

        for (Map.Entry<String, Class<? extends Template>> entry : types.entrySet()) {
            name = entry.getKey();
            template = entry.getValue();
            result.put(name, new Property(name, Switch.FIELD, name));
            for (Field field : template.getDeclaredFields()) {
                modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
                    fullName = name + "." + field.getName();
                    result.put(fullName, new Property(fullName, field, name));
                }
            }
        }
    }
}
