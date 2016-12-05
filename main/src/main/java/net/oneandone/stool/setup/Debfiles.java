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
package net.oneandone.stool.setup;

import net.oneandone.inline.Console;
import net.oneandone.stool.util.Environment;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

/** Generates man director for debian package*/
public class Debfiles {
    /** generate file hierarchy for Debian package */
    public static void main(String[] args) throws Exception {
        World world;
        FileNode target;
        FileNode man;
        FileNode profileD;
        Home home;

        if (args.length != 1) {
            throw new IllegalArgumentException();
        }
        world = World.create();
        target = world.file(args[0]);
        target.mkdir();

        man = target.join("usr/share/man");
        man.mkdirs();
        world.resource("templates/man").copyDirectory(man);

        profileD = target.join("etc/profile.d");
        profileD.mkdirs();

        home = new Home(Environment.loadSystem(), Console.create(), target, null);
        home.profile(profileD.join("stool.sh"), "");
        home.bashComplete(target.join("etc/bash_completion.d").mkdirs().join("stool"));
        System.exit(0);
    }
}
