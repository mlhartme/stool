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
package net.oneandone.stool.cli;

import net.oneandone.inline.Console;
import net.oneandone.stool.setup.Home;
import net.oneandone.stool.setup.UpgradeBuilder;
import net.oneandone.stool.util.Environment;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class SystemImport {
    private final Environment environment;
    private final Console console;
    private final boolean withConfig;
    private final FileNode home;
    private final FileNode from;

    public SystemImport(Globals globals, boolean withConfig, FileNode from) {
        this.environment = globals.environment;
        this.console = globals.console;
        this.withConfig = withConfig;
        this.home = globals.home;
        this.from = from;
    }

    public void run() throws IOException {
        Home h;
        UpgradeBuilder u;
        String version;

        from.checkDirectory();
        home.checkDirectory();
        h = new Home(environment, console, home, null);
        u = new UpgradeBuilder(environment, console, h, from);
        version = Main.versionString(home.getWorld());
        console.info.println("Stool " + version);
        console.info.println("Ready to import global config and stages " + from + " (version " + u.version() + ") into " + home + " (version " + version + ")");
        console.pressReturn();
        u.run(withConfig);
        console.info.println("Success.");
    }
}
