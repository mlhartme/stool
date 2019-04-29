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
package net.oneandone.stool.client.cli;

import net.oneandone.inline.Console;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Home;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Setup {
    private final World world;
    private final FileNode home;
    private final Console console;
    private final String version;
    private final boolean batch;

    public Setup(Globals globals, boolean batch) {
        this.world = globals.getWorld();
        this.home = globals.getHome();
        this.console = globals.getConsole();
        this.version = Main.versionString(world);
        this.batch = batch;
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
        Home.create(home);
        console.info.println("Done.");
        console.info.println("Make sure to add " + home.join("shell.inc") + " to your bash profile");
    }

    private void update() throws IOException {
        throw new IOException("TODO: upgrade");
    }
}