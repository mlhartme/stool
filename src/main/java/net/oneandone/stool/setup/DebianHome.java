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

import net.oneandone.stool.util.Environment;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.HashMap;

/** Create or update stool home during postinst configure. Does *not* care about permissions */
public class DebianHome {
    public static void main(String[] args) throws Exception {
        World world;
        Console console;
        FileNode home;
        boolean existing;

        if (args.length != 1) {
            throw new IllegalArgumentException();
        }
        world = new World();
        console = Console.create(world);
        home = world.file(args[0]);

        // migrate from 3.1.x
        existing = home.exists();
        if (existing) {
            migrate_3_1(console, home);
        }
        try {
            new Install(console, true, world.file("/usr/share/stool"), world.file("/usr/share/man"), new HashMap<>())
                    .debianHome("root", Environment.loadSystem(), home);
        } catch (Exception e) {
            if (!existing) {
                // make sure we don't leave any undefined home directory;
                try {
                    home.deleteTreeOpt();
                } catch (IOException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
        }
    }

    private static void migrate_3_1(Console console, FileNode home) throws IOException {
        rename(console, home.join("conf/overview.properties"), home.join("overview.properties"));
        rename(console, home.join("conf"), home.join("run"));
        rename(console, home.join("wrappers"), home.join("backstages"));
        delete(console, home.join("bin"));
    }

    public static void rename(Console console, FileNode old, FileNode now) throws IOException {
        if (old.exists()) {
            console.info.println("migrating: mv " + old + " " + now);
            old.move(now);
        }
    }
    public static void delete(Console console, FileNode old) throws IOException {
        if (old.exists()) {
            console.info.println("migrating: rm -rf " + old);
            old.deleteTree();
        }
    }
}
