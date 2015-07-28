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
import net.oneandone.stool.util.Files;
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
            console.info.println("updating home: " + home);
            migrate_3_1(console.info, home);
        } else {
            // make sure the setgid does not overrule the current group id
            home.getParent().execNoOutput("chmod", "g-s", ".");
            console.info.println("creating home: " + home);
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
            Files.exec(log, home, "mv", home.join("conf/overview.properties").getAbsolute(), home.join("overview.properties").getAbsolute());
            Files.exec(log, home, "sh", "-c", "find . -user servlet | xargs chown stool");
            Files.exec(log, home, "sh", "-c", "find . -perm 666 | xargs chmod 664");
            Files.exec(log, home, "sh", "-c", "find . -type d | xargs chmod g+s");
            Files.exec(log, home, "mv", home.join("conf").getAbsolute(), home.join("run").getAbsolute());
            Files.exec(log, home, "mv", home.join("wrappers").getAbsolute(), home.join("backstages").getAbsolute());
            Files.exec(log, home, "rm", "-rf", home.join("bin").getAbsolute());
            Files.exec(log, home, "chgrp", "/opt/ui/opt/tools/stool".equals(home.getAbsolute()) ? "users" : "stool", ".");
        }
    }
}
