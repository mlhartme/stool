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
package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Extensions {
    public final Map<String, Switch> extensions;

    public Extensions() {
        this.extensions = new HashMap<>();
    }

    public void add(String name, boolean enabled, Extension extension) {
        if (extension == null) {
            throw new IllegalArgumentException();
        }
        if (extensions.put(name, new Switch(enabled, extension)) != null) {
            throw new IllegalArgumentException("duplicate extension: " + name);
        }
    }

    public Map<String, FileNode> vhosts(Stage stage) throws IOException {
        Map<String, FileNode> result;

        result = new HashMap<>();
        for (Switch s : extensions.values()) {
            if (s.enabled) {
                result.putAll(s.extension.vhosts(stage));
            }
        }
        return result;
    }

    public void beforeStart(Stage stage) throws IOException {
        for (Switch s : extensions.values()) {
            if (s.enabled) {
                s.extension.beforeStart(stage);
            }
        }
    }

    public void beforeStop(Stage stage) throws IOException {
        for (Switch s : extensions.values()) {
            if (s.enabled) {
                s.extension.beforeStop(stage);
            }
        }
    }

    /** @param host  the vhost name, even if global vhosts config is false */
    public Map<String, String> contextParameter(Stage stage, String host, int httpPort, FileNode webinf) {
        Map<String, String> result;

        result = new HashMap<>();
        for (Switch s : extensions.values()) {
            if (s.enabled) {
                s.extension.contextParameter(stage, host, httpPort, webinf, result);
            }
        }
        return result;
    }

    public Switch get(String extension) {
        return extensions.get(extension);
    }
}
