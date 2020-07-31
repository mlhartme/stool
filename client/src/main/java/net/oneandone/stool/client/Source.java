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

import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** List of Apps. Represents .backstage */
public class Source {
    /* TODO: configurable
    public static final String PROPERTIES_FILE = "WEB-INF/classes/META-INF/stool.properties";
    public static final String PROPERTIES_PREFIX = ""; */
    public static final String PROPERTIES_FILE = "WEB-INF/classes/META-INF/pominfo.properties";
    public static final String PROPERTIES_PREFIX = "stool.";

    public static final String APP_ARGUMENT = "_app";

    //--

    public static final String SUBST = "_";

    public static boolean hasSubst(String name) {
        return name.contains(SUBST);
    }

    public static List<Source> findWarsAndCheck(FileNode directory, String stage) throws IOException {
        List<Source> wars;

        directory.checkDirectory();
        wars = findWars(directory);
        if (wars.isEmpty()) {
            throw new ArgumentException(directory.getAbsolute() + ": no wars found - did you build your project?");
        } else if (wars.size() > 1) {
            if (!stage.contains(SUBST)) {
                throw new ArgumentException(stage + ": missing '" + SUBST + "' in stage name to attach " + wars.size() + " stages");
            }
        }
        return wars;
    }

    public static List<Source> findWars(FileNode directory) throws IOException {
        List<Source> result;

        result = new ArrayList<>();
        addWars(directory, result);
        return result;
    }

    private static void addWars(FileNode directory, List<Source> result) throws IOException {
        Source war;

        war = warMatcher(directory);
        if (war != null) {
            result.add(war);
        } else {
            for (FileNode child : directory.list()) {
                if (child.isDirectory()) {
                    addWars(child, result);
                }
            }
        }
    }

    public static Source warMatcher(FileNode directory) throws IOException {
        List<FileNode> lst;

        if (!directory.join("pom.xml").isFile()) {
            return null;
        }
        lst = directory.find("target/*.war");
        switch (lst.size()) {
            case 0:
                return null;
            case 1:
                return new Source(directory, lst.get(0));
            default:
                throw new IOException("ambiguous: " + directory + " " + lst);
        }
    }

    //--

    public final FileNode directory;
    public final FileNode war;

    public Source(FileNode directory, FileNode war) {
        this.directory = directory;
        this.war = war;
    }

    public String subst(String name) throws IOException {
        return name.replace(SUBST, app());
    }

    public String app() throws IOException {
        String result;

        result = properties().get(APP_ARGUMENT);
        return result == null ? "app": result;
    }

    public Map<String, String> properties() throws IOException {
        Node<?> node;
        Properties all;
        Map<String, String> result;

        node = war.openZip().join(PROPERTIES_FILE);
        result = new HashMap<>();
        if (node.exists()) {
            all = node.readProperties();
            for (String property : all.stringPropertyNames()) {
                if (property.startsWith(PROPERTIES_PREFIX)) {
                    result.put(property.substring(PROPERTIES_PREFIX.length()), all.getProperty(property));
                }
            }
        }
        return result;
    }

    public Map<String, String> arguments(Map<String, String> explicit) throws IOException {
        Map<String, String> result;

        result = properties();
        result.putAll(explicit);
        result.remove(APP_ARGUMENT);
        return result;
    }
}
