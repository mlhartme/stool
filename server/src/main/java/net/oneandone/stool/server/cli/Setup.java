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
package net.oneandone.stool.server.cli;

import net.oneandone.inline.Console;
import net.oneandone.stool.server.util.Server;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Diff;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.List;

public class Setup {
    private final World world;
    private final Console console;
    private final FileNode home;
    private final String version;
    private final boolean batch;

    private String explicitConfig;

    public Setup(Globals globals, Console console, boolean batch) {
        this.world = globals.world;
        this.console = console;
        this.home = globals.home;
        this.version = Main.versionString(world);
        this.batch = batch;
        this.explicitConfig = null;
    }

    public void config(String str) {
        if (str != null && str.trim().isEmpty()) {
            str = null;
        }
        explicitConfig = str;
    }

    public void run() throws IOException {
        console.info.println("Stool " + version);
        if (home.isDirectory()) {
            update();
        } else {
            create();
        }
    }

    private void create() throws IOException {
        if (!batch) {
            console.info.println("Ready to create home directory: " + home.getAbsolute());
            console.pressReturn();
        }
        console.info.println("Creating " + home);
        Home.create(console, home, explicitConfig);
        console.info.println("Done.");
        console.info.println("Note: you can install the dashboard with");
        console.info.println("  stool create gav:net.oneandone.stool:dashboard:" + version + " " + home.getAbsolute() + "/system/dashboard");
    }

    private static final List<String> CONFIG = Strings.toList("config.json");

    private void update() throws IOException {
        Home h;
        String was;
        FileNode fresh;
        FileNode dest;
        String path;
        String left;
        String right;
        int count;

        h = new Home(console, home, null);
        was = h.version();
        if (!Server.majorMinor(was).equals(Server.majorMinor(version))) {
            throw new IOException("migration needed: " + was + " -> " + version + ": " + home.getAbsolute());
        }
        if (!batch) {
            console.info.println("Ready to update home directory " + was + " -> " + version + " : " + home.getAbsolute());
            console.pressReturn();
        }
        console.info.println("Updating " + home);
        fresh = world.getTemp().createTempDirectory();
        fresh.deleteDirectory();
        Home.create(console, fresh, null);
        count = 0;
        for (FileNode src : fresh.find("**/*")) {
            if (!src.isFile()) {
                continue;
            }
            path = src.getRelative(fresh);
            if (CONFIG.contains(path)) {
                continue;
            }
            dest = home.join(path);
            left = src.readString();
            right = dest.readString();
            if (!left.equals(right)) {
                console.info.println("U " + path);
                console.verbose.println(Strings.indent(Diff.diff(right, left), "  "));
                dest.writeString(left);
                count++;
            }
        }
        fresh.deleteTree();
        console.info.println("Done, " + count  + " file(s) updated.");
        console.info.println("Note: you can install the dashboard with");
        console.info.println("  stool create gav:net.oneandone.stool:dashboard:" + version + " " + home.getAbsolute() + "/dashboard");
    }

}
