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
package net.oneandone.stool.setup;

import net.oneandone.inline.Console;
import net.oneandone.stool.util.Files;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

/** Generates man director for debian package*/
public class Debfiles {
    /** generate file hierarchy for Debian package */
    public static void main(String[] args) throws Exception {
        World world;
        Console console;
        FileNode target;
        FileNode man;
        FileNode home;

        if (args.length != 1) {
            throw new IllegalArgumentException();
        }
        world = World.create();
        console = Console.create();
        target = world.file(args[0]);
        target.mkdir();

        man = target.join("man");
        Files.createStoolDirectory(console.verbose, man);
        world.resource("templates/man").copyDirectory(man);
        Files.stoolTree(console.verbose, man);

        home = target.join("stool-3.4");
        Home.create(console, home, "/usr/share/stool-3.4", null);

        System.exit(0);
    }
}
