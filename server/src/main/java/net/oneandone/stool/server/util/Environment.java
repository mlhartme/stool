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
package net.oneandone.stool.server.util;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

/**
 * Used instead of System.getenv. This way, I can properly define the interfaces/dependencies i have.
 * And it provides a simple mocking mechanism for integration tests
 */
public abstract class Environment {
    private static final String STOOL_USER = "STOOL_USER";
    private static final String STOOL_HOME = "STOOL_HOME";

    public static FileNode locateHome(World world) {
        String value;

        value = System.getenv(STOOL_HOME);
        if (value == null) {
            return world.getHome().join(".stool");
        } else {
            return world.file(value);
        }
    }

    public static FileNode locateLogs(FileNode home) {
        if (home.isDirectory()) {
            // stool is properly set up
            return home.join("logs");
        } else {
            // to run stool setup
            return home.getParent().join("stool-logs");
        }
    }

    public static String detectUser() {
        String name;

        name = System.getenv(Environment.STOOL_USER);
        return name != null ? name : System.getProperty("user.name");
    }

    public static String cisoTools() {
        return System.getenv("CISOTOOLS_HOME");
    }
}
