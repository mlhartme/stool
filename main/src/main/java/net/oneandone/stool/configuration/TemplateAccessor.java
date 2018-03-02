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

/** Handles Stool or Stage property. Converts between strings an objects and deals with reflection */
public class TemplateAccessor extends Accessor {
    private final FileNode templates;

    public TemplateAccessor(String name, FileNode templates) {
        super(name);
        this.templates = templates;
    }

    protected String doGet(Object configuration) {
        FileNode template;

        template = ((StageConfiguration) configuration).template;
        if (template.hasAnchestor(templates)) {
            return template.getRelative(templates);
        } else {
            return template.getAbsolute();
        }
    }

    protected void doSet(Object configuration, String template) {
        FileNode dir;
        StageConfiguration config;

        config = (StageConfiguration) configuration;
        if (config.template.getName().equals(template)) {
            // no changes
            return;
        }
        dir = templates.file(template);
        if (!dir.isDirectory()) {
            throw new ArgumentException("no such template: " + template);
        }
        config.template = dir;
        try {
            config.templateEnv = Variable.defaultMap(Variable.scanTemplate(dir).values());
        } catch (IOException e) {
            throw new ArgumentException("cannot set template: " + e.getMessage(), e);
        }
    }
}
