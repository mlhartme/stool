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
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.io.PrintWriter;
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
            migrate_3_1(console.info, home);
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

    private static void migrate_3_1(PrintWriter log, FileNode home) throws IOException {
        if (home.join("bin").isDirectory()) {
            log.println("migrating 3.1 -> 3.2: " + home);
            run(log, home, "mv", home.join("conf/overview.properties").getAbsolute(), home.join("overview.properties").getAbsolute());
            run(log, home, "chown", "stool", home.join("overview.properties").getAbsolute());
            run(log, home, "mv", home.join("conf").getAbsolute(), home.join("run").getAbsolute());
            run(log, home, "mv", home.join("wrappers").getAbsolute(), home.join("backstages").getAbsolute());
            run(log, home, "rm", "-rf", home.join("bin").getAbsolute());
            fixPermissions(log, home);
        }
    }

    public static void fixPermissions(PrintWriter log, FileNode dir) throws IOException {
        run(log, dir, "chgrp", "-r", "stool", ".");
        run(log, dir, "sh", "-c", "find . -type d | xargs chmod g+s");
    }

    public static void run(PrintWriter log, FileNode home, String ... cmd) throws IOException {
        log.println("[" + home + "] " + Separator.SPACE.join(cmd));
        home.execNoOutput(cmd);
    }
}
