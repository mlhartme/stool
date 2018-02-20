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
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.Map;

/** Handles Stool or Stage property. Converts between strings an objects and deals with reflection */
public class TemplateEnvProperty extends Property {
    private static final String PREFIX = "template.";

    private final FileNode templates;
    private final String subname;

    public TemplateEnvProperty(String name, FileNode templates, String subname) {
        super(name);
        this.templates = templates;
        this.subname = subname;
    }

    protected String doGet(Object configuration) {
        String result;

        result = ((StageConfiguration) configuration).templateEnv.get(subname);
        return result == null ? "" : result;
    }

    protected void doSet(Object configuration, String value) {
        StageConfiguration config;
        FileNode src;
        Map<String, Variable> environment;
        Variable variable;

        config = (StageConfiguration) configuration;
        src = templates.join(config.template);
        if (!src.isDirectory()) {
            throw new ArgumentException("cannot set template variable '" + subname + "': template '" + config.template + "' is undefined");
        }
        try {
            environment = Variable.scanTemplate(src);
        } catch (IOException e) {
            throw new ArgumentException("cannot set template variable '" + subname + "': cannot load template '" + config.template + ": " + e.getMessage(), e);
        }
        variable = environment.get(subname);
        if (variable == null) {
            throw new ArgumentException("cannot set template variable '" + subname + "': no such variable in template '" + config.template);
        }
        try {
            variable.parse(value);
        } catch (RuntimeException e) {
            throw new ArgumentException("cannot set template variable '" + subname + "': invalid value '" + value + "'", e);
        }
        config.templateEnv.put(subname, value);
    }

    public static Property createOpt(FileNode home, String name) {
        if (!name.startsWith(PREFIX)) {
            return null;
        }
        return new TemplateEnvProperty(name, home, name.substring(PREFIX.length()));
    }
}
