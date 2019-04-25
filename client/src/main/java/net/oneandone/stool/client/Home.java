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

import net.oneandone.stool.client.cli.Main;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

/**
 * Stool home directory. In unix file system hierarchy this comes close to the lib directory - although it contains
 * etc stuff (config.json) and log files.
 */
public class Home {
    public static void create(FileNode home) throws IOException {
        Home obj;

        home.checkNotExists();
        obj = new Home(home);
        obj.create();
    }

    private final FileNode dir;

    public Home(FileNode dir) {
        this.dir = dir;
    }

    public void create() throws IOException {
        World world;

        dir.mkdir();
        world = dir.getWorld();
        world.resource("files/home").copyDirectory(dir);
        profile(dir.join("shell.inc"), file("files/sourceBashComplete"));
        bashComplete(dir.join("bash.complete"));
        versionFile().writeString(Main.versionString(world));
    }

    public void profile(FileNode dest, String extra) throws IOException {
        dest.writeString(file("files/profile") + extra);
    }

    public void bashComplete(FileNode dest) throws IOException {
        dest.writeString(file("files/bash.complete"));
    }

    private String file(String name) throws IOException {
        return dir.getWorld().resource(name).readString();
    }

    public String version() throws IOException {
        return versionFile().readString().trim();
    }

    public FileNode versionFile() {
        return dir.join("version");
    }
}
