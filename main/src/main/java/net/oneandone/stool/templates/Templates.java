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
import java.util.HashMap;
import java.util.Map;

public class Templates {
    public final Map<String, Switch> templates;

    public Templates() {
        this.templates = new HashMap<>();
    }

    public void add(String name, boolean enabled, Template template) {
        if (template == null) {
            throw new IllegalArgumentException();
        }
        if (templates.put(name, new Switch(enabled, template)) != null) {
            throw new IllegalArgumentException("duplicate template: " + name);
        }
    }

    public Map<String, FileNode> vhosts(Stage stage) throws IOException {
        Map<String, FileNode> result;

        result = new HashMap<>();
        for (Switch s : templates.values()) {
            if (s.enabled) {
                result.putAll(s.template.vhosts(stage));
            }
        }
        return result;
    }

    public void beforeStart(Stage stage) throws IOException {
        for (Switch s : templates.values()) {
            if (s.enabled) {
                s.template.beforeStart(stage);
            }
        }
    }

    public void beforeStop(Stage stage) throws IOException {
        for (Switch s : templates.values()) {
            if (s.enabled) {
                s.template.beforeStop(stage);
            }
        }
    }

    /** @param host  the vhost name, even if global vhosts config is false */
    public Map<String, String> contextParameter(Stage stage, String host, int httpPort, FileNode webinf) {
        Map<String, String> result;

        result = new HashMap<>();
        for (Switch s : templates.values()) {
            if (s.enabled) {
                s.template.contextParameter(stage, host, httpPort, webinf, result);
            }
        }
        return result;
    }

    public Switch get(String template) {
        return templates.get(template);
    }

    public Map<String, String> tomcatOpts(Stage stage) {
        Map<String, String> result;

        result = new HashMap<>();
        for (Switch s : templates.values()) {
            if (s.enabled) {
                s.template.tomcatOpts(stage, result);
            }
        }
        return result;
    }

    public Map<String, Object> containerOpts(Stage stage) throws IOException {
        Map<String, Object> result;

        result = new HashMap<>();
        for (Switch s : templates.values()) {
            if (s.enabled) {
                s.template.containerOpts(stage, result);
            }
        }
        return result;
    }

    public void files(Stage stage, FileNode dest) throws IOException {
        for (Switch s : templates.values()) {
            if (s.enabled) {
                s.template.files(stage, dest);
            }
        }
    }
}
