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
public class Secrets {
    public static URI portus(World world) throws IOException {
        Properties tmp;

        tmp = secrets(world).join("secrets.properties").readProperties();
        return URI.create(tmp.getProperty("portus"));
    }

    public static FileNode secrets(World world) throws IOException {
        return world.getHome().join(".fault/net.oneandone.stool:stool").checkDirectory();
    }

    private Secrets() {
    }
}
