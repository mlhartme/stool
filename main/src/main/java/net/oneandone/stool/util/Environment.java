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
package net.oneandone.stool.util;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;

import java.util.HashMap;
import java.util.Map;

/**
 * Used instead of System.getenv. This way, I can properly define the interfaces/dependencies i have.
 * And it provides a simple mocking mechanism for integration tests
 */
public class Environment {
    public static final String STOOL_USER = "STOOL_USER";
    public static final String STOOL_HOME = "STOOL_HOME";

    private static final String JAVA_HOME = "JAVA_HOME";
    private static final String MAVEN_HOME = "MAVEN_HOME";
    private static final String MAVEN_OPTS = "MAVEN_OPTS";

    public static Environment loadSystem() {
        Environment result;
        Map<String, String> system;
        String key;
        String value;
        String oldValue;

        result = new Environment();
        system = System.getenv();
        for (Map.Entry<String, String> entry : system.entrySet()) {
            key = entry.getKey();
            value = entry.getValue();
            oldValue = result.set(key, value);
            if (oldValue != null) {
                if (!oldValue.equals(value)) {
                    throw new IllegalStateException(key + ": " + value + " vs " + oldValue);
                }
                throw new IllegalStateException("duplicate assignment: " + key + "=" + value);
            }
        }
        return result;
    }

    //--

    private final Map<String, String> properties;

    public Environment() {
        this.properties = new HashMap<>();
    }

    public String set(String key, String value) {
        return properties.put(key, value);
    }

    public String get(String key) {
        String value;

        value = getOpt(key);
        if (value == null) {
            throw new IllegalStateException("property not found: " + key);
        }
        return value;
    }

    public String getOpt(String key) {
        return properties.get(key);
    }

    //--

    public int hashCode() {
        return properties.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof Environment) {
            return properties.equals(((Environment) obj).properties);
        }
        return false;
    }

    public String toString() {
        return properties.toString();
    }

    //--

    public String detectUser() {
        String name;

        name = getOpt(Environment.STOOL_USER);
        return name != null ? name : System.getProperty("user.name");
    }

    public FileNode locateHome(World world) {
        String value;

        value = getOpt(STOOL_HOME);
        if (value == null) {
            return world.getHome().join(".stool");
        } else {
            return world.file(value);
        }
    }

    public void setHome(FileNode home) {
        set(STOOL_HOME, home.getAbsolute());
    }

    public void setMavenHome(String mavenHome) {
        set(MAVEN_HOME, mavenHome);
    }

    public void setMavenOpts(String mavenOpts) {
        set(MAVEN_OPTS, mavenOpts);
    }

    public void setJavaHome(String javaHome) {
        set(JAVA_HOME, javaHome);
    }

    public void save(Launcher launcher) {
        String value;

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            value = entry.getValue();
            if (value != null) {
                launcher.env(entry.getKey(), value);
            } else {
                launcher.getBuilder().environment().remove(entry.getKey());
            }
        }
    }
}
