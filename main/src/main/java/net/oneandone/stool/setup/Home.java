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

import com.google.gson.Gson;
import net.oneandone.inline.Console;
import net.oneandone.setenv.Setenv;
import net.oneandone.stool.cli.Main;
import net.oneandone.stool.configuration.Autoconf;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.extensions.ExtensionsFactory;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Stool home directory. In unix file system hierarchy this comes close to the lib directory - although it contains
 * etc stuff (config.json) and log files.
 */
public class Home {
    public static void create(Console console, FileNode home, String config, boolean debian) throws IOException {
        create(console, home, home.getAbsolute(), config, debian);
    }

    public static void create(Console console, FileNode home, String homeVariable, String config, boolean debian) throws IOException {
        RmRfThread cleanup;
        Home obj;

        home.checkNotExists();
        cleanup = new RmRfThread(console);
        cleanup.add(home);
        Runtime.getRuntime().addShutdownHook(cleanup);
        obj = new Home(console, home, group(home.getWorld()), config);
        obj.setHomeVariable(homeVariable);
        obj.create(debian);
        // ok, no exceptions - we have a proper install directory: no cleanup
        Runtime.getRuntime().removeShutdownHook(cleanup);
    }

    public static String group(World world) throws IOException {
        FileNode file;
        String result;

        file = world.getTemp().createTempFile();
        result = file.getGroup().toString();
        file.deleteFile();
        return result;
    }

    private final Console console;
    public final FileNode dir;
    private String homeVariable;
    private final String group;
    /** json, may be null */
    private final String explicitConfig;

    public Home(Console console, FileNode dir, String group, String explicitConfig) {
        this.console = console;
        this.dir = dir;
        this.homeVariable = dir.getAbsolute();
        this.group = group;
        this.explicitConfig = explicitConfig;
    }

    public void setHomeVariable(String value) {
        this.homeVariable = value;
    }

    public void create(boolean debian) throws IOException {
        World world;
        Gson gson;
        StoolConfiguration conf;

        gson = gson();
        Files.createStoolDirectory(console.verbose, dir);
        exec("chgrp", group, dir.getAbsolute());
        // chgrp overwrites the permission - thus, i have to re-set permissions
        exec("chmod", "2775", dir.getAbsolute());

        world = dir.getWorld();
        Files.template(console.verbose, world.resource("templates/home"), dir, variables());
        conf = Autoconf.stool(dir, debian);
        if (explicitConfig != null) {
            conf = conf.createPatched(gson, explicitConfig);
        }
        conf.save(gson, dir);
        if (!conf.downloadCache.exists()) {
            Files.createStoolDirectory(console.verbose, conf.downloadCache);
        }
        for (String name : new String[]{"extensions", "backstages", "logs", "service-wrapper", "run", "tomcat", "system"}) {
            Files.createStoolDirectory(console.verbose, dir.join(name));
        }
        Files.stoolFile(versionFile().writeString(Main.versionString(world)));
        Files.stoolFile(dir.join("run/locks").mkfile());
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
        gson = Session.gson(world, ExtensionsFactory.create(world));
        return gson;
    }

    public void exec(String ... cmd) throws IOException {
        Files.exec(console.info, dir, cmd);
    }

    private Map<String, String> variables() {
        Map<String, String> variables;

        variables = new HashMap<>();
        variables.put("stool.home", homeVariable);
        variables.put("setenv.rc", Setenv.get().setenvBash());
        return variables;
    }
}
