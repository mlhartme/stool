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
package net.oneandone.stool.util;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

// TODO: does not work in public
public class TestProperties {
    public static TestProperties load(World world) throws IOException {
        FileNode file;
        Properties p;
        String portus;

        file = world.guessProjectHome(TestProperties.class).join("test.properties");
        if (!file.exists()) {
            file = secrets(world).join("test.properties");
            if (!file.exists()) {
                throw new IOException("missing test.properties");
            }
        }
        p = file.readProperties();
        portus = p.getProperty("portus");
        return new TestProperties(portus == null ? null : URI.create(portus), p.getProperty("toolkit"));
    }

    private static FileNode secrets(World world) throws IOException {
        return world.getHome().join(".fault/net.oneandone.stool:stool").checkDirectory();
    }

    public final URI portus;

    /** may be null */
    public final String toolkit;

    private TestProperties(URI portus, String toolkit) {
        this.portus = portus;
        this.toolkit = toolkit;
    }
}
