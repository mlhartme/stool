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

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class Templates {
    public static final Field FIELD;

    static {
        try {
            FIELD = Templates.class.getDeclaredField("selected");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    public final Map<String, Template> templates;
    private String selected;

    public Templates() {
        this.templates = new HashMap<>();
        this.selected = null;
    }

    public String getSelected() {
        return selected;
    }

    public String marker(String template) {
        return template.equals(selected) ? "+" : "-";
    }

    public void add(String name, boolean select, Template template) {
        if (template == null) {
            throw new IllegalArgumentException();
        }
        if (select) {
            this.selected = name;
        }
        if (templates.put(name, template) != null) {
            throw new IllegalArgumentException("duplicate template: " + name);
        }
    }

    /** @param host  the vhost name, even if global vhosts config is false */
    public Map<String, String> contextParameter(Stage stage, String host, int httpPort, FileNode webinf) {
        Map<String, String> result;

        result = new HashMap<>();
        templates.get(selected).contextParameter(stage, host, httpPort, webinf, result);
        return result;
    }

    public Template get(String template) {
        return templates.get(template);
    }

    public Map<String, Object> containerOpts(Stage stage) throws IOException {
        Map<String, Object> result;

        result = new HashMap<>();
        templates.get(selected).containerOpts(stage, result);
        return result;
    }

    public void files(Stage stage, FileNode dest) throws IOException {
        templates.get(selected).files(stage, dest);
    }
}
