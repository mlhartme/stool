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
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.oneandone.inline.Console;
import net.oneandone.stool.cli.Main;
import net.oneandone.stool.configuration.Autoconf;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.extensions.ExtensionsFactory;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Diff;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Stool's library directory. Lib holds the stool-maintained files like backstages and downloads.
 */
public class Lib {
    public static void create(Console console, FileNode lib, String config) throws IOException {
        RmRfThread cleanup;

        lib.checkNotExists();
        cleanup = new RmRfThread(console);
        cleanup.add(lib);
        Runtime.getRuntime().addShutdownHook(cleanup);
        new Lib(console, lib, group(lib.getWorld()), config).create();
        // ok, no exceptions - we have a proper install directory: no cleanup
        Runtime.getRuntime().removeShutdownHook(cleanup);
    }

    private static String group(World world) throws IOException {
        FileNode file;
        String result;

        file = world.getTemp().createTempFile();
        result = file.getGroup().toString();
        file.deleteFile();
        return result;
    }

    private final Console console;
    private final FileNode dir;
    private final String group;
    /** json, may be null */
    private final String explicitConfig;

    public Lib(Console console, FileNode dir, String group, String explicitConfig) {
        this.console = console;
        this.dir = dir;
        this.group = group;
        this.explicitConfig = explicitConfig;
    }

    public void create() throws IOException {
        World world;
        Gson gson;
        StoolConfiguration conf;

        world = dir.getWorld();
        gson = Session.gson(world, ExtensionsFactory.create(world));
        Files.createStoolDirectory(console.verbose, dir);
        exec("chgrp", group, dir.getAbsolute());
        // chgrp overwrites the permission - thus, i have to re-set permissions
        exec("chmod", "2775", dir.getAbsolute());

        Files.template(console.verbose, world.resource("templates/lib"), dir, variables());
        conf = Autoconf.stool(dir);
        if (explicitConfig != null) {
            conf = conf.createPatched(gson, explicitConfig);
        }
        conf.save(gson, dir);
        if (!conf.downloadCache.exists()) {
            Files.createStoolDirectory(console.verbose, conf.downloadCache);
        }
        for (String name : new String[]{"extensions", "backstages", "logs", "service-wrapper", "run", "run/users", "tomcat"}) {
            Files.createStoolDirectory(console.verbose, dir.join(name));
        }
        Files.stoolFile(dir.join("run/locks").mkfile());
    }

    public Map<String, String> variables() {
        Map<String, String> variables;

        variables = new HashMap<>();
        variables.put("stool.lib", dir.getAbsolute());
        return variables;
    }

    //-- upgrade

    public void upgrade(String oldVersion) throws IOException {
        String newVersion;

        newVersion = Main.versionString(dir.getWorld());
        console.info.println("upgrade " + oldVersion + " -> " + newVersion);
        if (oldVersion.startsWith("3.3.")) {
            upgrade_33_34(dir);
        } else if (oldVersion.startsWith(("3.4."))) {
            console.info.println("nothing to do");
        } else {
            throw new IOException("don't know how to upgrade " + oldVersion + " -> " + newVersion);
        }
    }

    private void upgrade_33_34(FileNode lib) throws IOException {
        exec("rm", lib.join("version").getAbsolute());
        doUpgrade(stool33_34(), stage33_34());
    }

    // TODO: ugly ...
    
    private static Lib upgradeLib = null;
    private static FileNode upgradeBackstage = null;

    private void doUpgrade(Upgrade stoolMapper, Upgrade stageMapper) throws IOException {
        upgradeLib = this;
        doUpgradeStool(stoolMapper);
        for (FileNode oldBackstage : dir.join("backstages").list()) {
            console.info.println("upgrade " + oldBackstage);
            // TODO
            upgradeBackstage = oldBackstage;
            transform(oldBackstage.join("config.json"), stageMapper);
        }
    }

    private void doUpgradeStool(Upgrade stoolMapper) throws IOException {
        transform(dir.join("config.json"), stoolMapper);
    }

    private void transform(FileNode json, Upgrade mapper) throws IOException {
        String in;
        String out;

        console.verbose.println("transform " + json.getAbsolute());
        in = json.readString();
        out = Transform.transform(in, mapper);
        if (!in.equals(out)) {
            console.info.println("M " + json.getAbsolute());
            console.info.println(Strings.indent(Diff.diff(in, out), "  "));
            json.writeString(out);
        }
    }

    private void exec(String ... cmd) throws IOException {
        Files.exec(console.info, dir, cmd);
    }

    //--

    public static Upgrade stool33_34() {
        return new Upgrade() {
            JsonElement promptTransform(JsonElement e) {
                String str;

                str = e.getAsString();
                str = str.replace("\\=", "\\u@\\h \\w \\$ ");
                return new JsonPrimitive(str);
            }

            String contactAdminRename() {
                return "admin";
            }
        };
    }

    public static Upgrade stage33_34() {
        return new Upgrade() {
            JsonElement extensionsTransform(JsonElement e) {
                e.getAsJsonObject().remove("-pustefix.editor");
                e.getAsJsonObject().remove("+pustefix.editor");
                return e;
            }
            void sslUrlRemove() {
            }
            String suffixesRename() {
                return "urls";
            }
        };
    }
}
