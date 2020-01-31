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
package net.oneandone.stool.client;

import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.Properties;

public class App {
    // TODO: per app configurable ...
    public static final String PROPERTIES_FILE = "WEB-INF/classes/META-INF/stool.properties";
    public static final String PROPERTIES_PREFIX = "";

    public final Reference reference;
    public final String path;

    public App(Reference reference, String path) {
        this.reference = reference;
        this.path = path;
    }

    public static String app(FileNode war) throws IOException {
        return properties(war).getProperty("app", "app");
    }

    public static Properties properties(FileNode war) throws IOException {
        Node<?> node;
        Properties all;
        Properties result;

        node = war.openZip().join(PROPERTIES_FILE);
        result = new Properties();
        if (node.exists()) {
            all = node.readProperties();
            for (String property : all.stringPropertyNames()) {
                if (property.startsWith(PROPERTIES_PREFIX)) {
                    result.setProperty(property.substring(PROPERTIES_PREFIX.length()), all.getProperty(property));
                }
            }
        }
        return result;
    }
}
