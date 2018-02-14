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

import com.google.gson.Gson;
import net.oneandone.inline.Console;
import net.oneandone.setenv.Setenv;
import net.oneandone.stool.cli.Main;
import net.oneandone.stool.configuration.Autoconf;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;

/**
 * Stool home directory. In unix file system hierarchy this comes close to the lib directory - although it contains
 * etc stuff (config.json) and log files.
 */
public class Home {
    public static void create(Environment environment, Console console, FileNode home, String config) throws IOException {
        RmRfThread cleanup;
        Home obj;

        home.checkNotExists();
        cleanup = new RmRfThread(console);
        cleanup.add(home);
        Runtime.getRuntime().addShutdownHook(cleanup);
        obj = new Home(environment, console, home, config);
        obj.create();
        // ok, no exceptions - we have a proper install directory: no cleanup
        Runtime.getRuntime().removeShutdownHook(cleanup);
    }

    private final Environment environment;
    private final Console console;
    public final FileNode dir;
    /** json, may be null */
    private final String explicitConfig;

    public Home(Environment environment, Console console, FileNode dir, String explicitConfig) {
        this.environment = environment;
        this.console = console;
        this.dir = dir;
        this.explicitConfig = explicitConfig;
    }

    public void create() throws IOException {
        World world;
        Gson gson;
        StoolConfiguration conf;

        gson = gson();
        dir.mkdir();

        world = dir.getWorld();
        world.resource("files/home").copyDirectory(dir);
        profile(dir.join("shell.rc"),
                file("files/sourceBashComplete"));
        bashComplete(dir.join("bash.complete"));
        conf = Autoconf.stool(environment, dir);
        if (explicitConfig != null) {
            conf = conf.createPatched(gson, explicitConfig);
        }
        conf.save(gson, dir);
        conf.downloadCache.mkdirOpt();
        for (String name : new String[]{"backstages", "logs", "run", "system"}) {
            dir.join(name).mkdir();
        }
        Autoconf.templates(dir.join("templates"), console);
        versionFile().writeString(Main.versionString(world));
        dir.join("run/locks").mkfile();
    }

    public void profile(FileNode dest, String extra) throws IOException {
        dest.writeString(Setenv.get().setenvBash() + file("files/profile") + extra);
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

    public Gson gson() {
        World world;
        Gson gson;

        world = dir.getWorld();
        gson = Session.gson(world);
        return gson;
    }

    public void exec(String ... cmd) throws IOException {
        console.info.println("[" + dir + "] " + Separator.SPACE.join(cmd));
        dir.execNoOutput(cmd);
    }
}
