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

public class Home {
    private final Console console;
    private final FileNode home;
    private final String group;
    /** json, may be null */
    private final String explicitConfig;

    public Home(Console console, FileNode home, String group, String explicitConfig) {
        this.console = console;
        this.home = home;
        this.group = group;
        this.explicitConfig = explicitConfig;
    }

    public void create() throws IOException {
        World world;
        Gson gson;
        StoolConfiguration conf;

        world = home.getWorld();
        gson = Session.gson(world, ExtensionsFactory.create(world));
        Files.createStoolDirectory(console.verbose, home);
        exec("chgrp", group, home.getAbsolute());
        // chgrp overwrites the permission - thus, i have to re-set permissions
        exec("chmod", "2775", home.getAbsolute());

        world.resource("templates/maven-settings.xml").copyFile(home.join("maven-settings.xml"));
        conf = new StoolConfiguration(downloadCache());
        tuneHostname(conf);
        if (explicitConfig != null) {
            conf = conf.createPatched(gson, explicitConfig);
        }
        conf.save(gson, home);

        for (String dir : new String[]{"extensions", "backstages", "logs", "service-wrapper", "run", "run/users", "tomcat"}) {
            Files.createStoolDirectory(console.verbose, home.join(dir));
        }
        Files.stoolFile(home.join("run/locks").mkfile());
    }

    private FileNode downloadCache() {
        FileNode directory;

        if (OS.CURRENT == OS.MAC) {
            directory = (FileNode) console.world.getHome().join("Downloads");
            if (directory.isDirectory()) {
                return directory;
            }
        }
        return home.join("downloads");
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

        file = home.join("version");
        if (file.isFile()) {
            oldVersion = file.readString();
        } else {
            oldVersion = guessVersion();
        }
        newVersion = JavaSetup.versionObject().toString();
        console.info.println("upgrade " + oldVersion + " -> " + newVersion);
        if (oldVersion.startsWith("3.1.")) {
            upgrade_31_32(home);
            upgrade_32_33(home);
        } else if (oldVersion.startsWith("3.2.")) {
            upgrade_32_33(home);
        } else if (oldVersion.startsWith(("3.3."))) {
            console.info.println("nothing to do");
        } else {
            throw new IOException("don't know how to upgrade");
        }
        file.writeString(newVersion);
    }

    private String guessVersion() throws IOException {
        if (home.join("bin").isDirectory() && !home.join("bin/home").isLink()) {
            return "3.1.x";
        }
        if (home.join("overview.properties").isFile()) {
            return "3.2.x";
        }
        throw new IOException("unknown version of home directory: " + home);
    }

    private void upgrade_31_32(FileNode home) throws IOException {
        exec("mv", home.join("conf/overview.properties").getAbsolute(), home.join("overview.properties").getAbsolute());
        exec("sh", "-c", "find . -user servlet | xargs chown stool");
        exec("sh", "-c", "find . -perm 666 | xargs chmod 664");
        exec("sh", "-c", "find . -type d | xargs chmod g+s");
        exec("mv", home.join("conf").getAbsolute(), home.join("run").getAbsolute());
        exec("mv", home.join("wrappers").getAbsolute(), home.join("backstages").getAbsolute());
        exec("rm", "-rf", home.join("bin").getAbsolute());
        exec("chgrp", group, ".");
        doUpgrade(stool31_32(), stage31_32());
    }

    private void upgrade_32_33(FileNode home) throws IOException {
        // remove the old overview, but keep it's configuration
        exec("rm", "-rf", home.join("backstages/overview").getAbsolute());
        exec("rm", "-rf", home.join("overview").getAbsolute());
        exec("mv", home.join("overview.properties").getAbsolute(), home.join("dashboard.properties").getAbsolute());

        ports_32_33(home);
        doUpgrade(stool32_33(), stage32_33());
    }

    private void ports_32_33(FileNode home) throws IOException {
        FileNode backstages;
        Node ports32;
        Pool pool;

        backstages = home.join("backstages");
        pool = new Pool(home.join("run/ports"), 2, Integer.MAX_VALUE, backstages);
        for (Node stage : home.list()) {
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
        transform(home.join("config.json"), stoolMapper);
    }

    private void doUpgradeStage(Object stageMapper) throws IOException {
        for (FileNode oldBackstage : home.join("backstages").list()) {
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
        Files.exec(console.info, home, cmd);
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
        };
    }

    public static Object stage32_33() {
        return new Object() {
        };
    }
}
