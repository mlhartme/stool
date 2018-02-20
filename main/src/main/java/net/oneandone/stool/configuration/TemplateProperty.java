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
package net.oneandone.stool.configuration;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.templates.Variable;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Handles Stool or Stage property. Converts between strings an objects and deals with reflection */
public class TemplateProperty extends Property {
    private final FileNode templates;

    public TemplateProperty(String name, FileNode templates) {
        super(name);
        this.templates = templates;
    }

    protected String doGet(Object configuration) {
        return ((StageConfiguration) configuration).template;
    }

    protected void doSet(Object configuration, String template) {
        FileNode src;
        StageConfiguration config;

        config = (StageConfiguration) configuration;
        if (config.template.equals(template)) {
            // no changes
            return;
        }
        src = templates.join(template);
        if (!src.isDirectory()) {
            throw new ArgumentException("no such template: " + template);
        }
        config.template = template;
        try {
            config.templateEnv = Variable.defaultMap(Variable.scanTemplate(src).values());
        } catch (IOException e) {
            throw new ArgumentException("cannot set template: " + e.getMessage(), e);
        }
    }


}
