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
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.extensions.ExtensionsFactory;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Pool;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.util.Diff;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Stool's library directory. Lib is an install directory without man and bin. */
public class Lib {
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

        world.resource("templates/maven-settings.xml").copyFile(dir.join("maven-settings.xml"));
        conf = new StoolConfiguration(downloadCache());
        tuneHostname(conf);
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

    private FileNode downloadCache() {
        FileNode directory;

        if (OS.CURRENT == OS.MAC) {
            directory = (FileNode) console.world.getHome().join("Downloads");
            if (directory.isDirectory()) {
                return directory;
            }
        }
        return dir.join("downloads");
    }

    private void tuneHostname(StoolConfiguration conf) {
        try {
            conf.hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            console.info.println("WARNING: cannot configure hostname: " + e.getMessage() + ". Using " + conf.hostname);
        }
    }

    //-- upgrade

    public void upgrade() throws IOException {
        FileNode file;
        String oldVersion;
        String newVersion;

        file = dir.join("version");
        if (file.isFile()) {
            oldVersion = file.readString();
        } else {
            oldVersion = guessVersion();
        }
        newVersion = JavaSetup.versionObject().toString();
        console.info.println("upgrade " + oldVersion + " -> " + newVersion);
        if (oldVersion.startsWith("3.1.")) {
            upgrade_31_32(dir);
            upgrade_32_33(dir);
        } else if (oldVersion.startsWith("3.2.")) {
            upgrade_32_33(dir);
        } else if (oldVersion.startsWith(("3.3."))) {
            console.info.println("nothing to do");
        } else {
            throw new IOException("don't know how to upgrade");
        }
        file.writeString(newVersion);
    }

    private String guessVersion() throws IOException {
        if (dir.join("bin").isDirectory() && !dir.join("bin/lib").isLink()) {
            return "3.1.x";
        }
        if (dir.join("overview.properties").isFile()) {
            return "3.2.x";
        }
        throw new IOException("unknown version of lib directory: " + dir);
    }

    private void upgrade_31_32(FileNode lib) throws IOException {
        exec("mv", lib.join("conf/overview.properties").getAbsolute(), lib.join("overview.properties").getAbsolute());
        exec("sh", "-c", "find . -user servlet | xargs chown stool");
        exec("sh", "-c", "find . -perm 666 | xargs chmod 664");
        exec("sh", "-c", "find . -type d | xargs chmod g+s");
        exec("mv", lib.join("conf").getAbsolute(), lib.join("run").getAbsolute());
        exec("mv", lib.join("wrappers").getAbsolute(), lib.join("backstages").getAbsolute());
        exec("rm", "-rf", lib.join("bin").getAbsolute());
        exec("chgrp", group, ".");
        doUpgrade(stool31_32(), stage31_32());
    }

    private void upgrade_32_33(FileNode lib) throws IOException {
        // remove the old overview, but keep it's configuration
        exec("rm", "-rf", lib.join("backstages/overview").getAbsolute());
        exec("rm", "-rf", lib.join("overview").getAbsolute());
        exec("mv", lib.join("overview.properties").getAbsolute(), lib.join("dashboard.properties").getAbsolute());

        ports_32_33(lib);
        doUpgrade(stool32_33(), stage32_33());
    }

    private void ports_32_33(FileNode lib) throws IOException {
        FileNode backstages;
        Node ports32;
        Pool pool;

        backstages = lib.join("backstages");
        pool = new Pool(lib.join("run/ports"), 2, Integer.MAX_VALUE, backstages);
        for (Node stage : lib.list()) {
            ports32 = stage.join("ports");
            if (ports32.isFile()) {
                for (Host32 host32 : Host32.load(ports32)) {
                    pool.add(host32.upgrade(stage));
                }
            }
        }
        pool.save();
    }

    private void doUpgrade(Object stoolMapper, Object stageMapper) throws IOException {
        doUpgradeStool(stoolMapper);
        doUpgradeStage(stageMapper);
    }

    private void doUpgradeStool(Object stoolMapper) throws IOException {
        transform(dir.join("config.json"), stoolMapper);
    }

    private void doUpgradeStage(Object stageMapper) throws IOException {
        for (FileNode oldBackstage : dir.join("backstages").list()) {
            transform(oldBackstage.join("config.json"), stageMapper);
        }
    }

    private void transform(FileNode json, Object mapper) throws IOException {
        String in;
        String out;

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

    public static Object stool31_32() {
        return new Object() {
        };
    }

    public static Object stage31_32() {
        return new Object() {
        };
    }

    public static Object stool32_33() {
        return new Object() {
            void portDashboardRemove() {
            }
            void errorToolRemove() {
            }
            void updateInterval() {
            }
        };
    }

    public static Object stage32_33() {
        return new Object() {
        };
    }
}
