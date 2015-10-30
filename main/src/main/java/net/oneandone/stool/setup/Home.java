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

import net.oneandone.stool.configuration.Property;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.extensions.ExtensionsFactory;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Pool;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.util.Diff;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

public class Home {
    public static final String STOOL_UPDATE_CHECKED = ".stool.update.checked";

    private final Console console;
    private final FileNode home;
    private final String user;
    private final String group;
    private final boolean shared;
    private final Map<String, String> globalProperties;

    public Home(Console console, FileNode home, boolean shared, Map<String, String> globalProperties) {
        this(console, home, "stool", "stool", shared, globalProperties);
    }

    public Home(Console console, FileNode home, String user, String group, boolean shared, Map<String, String> globalProperties) {
        this.console = console;
        this.home = home;
        this.user = user;
        this.group = group;
        this.shared = shared;
        this.globalProperties = globalProperties;
    }

    public void create() throws IOException {
        StoolConfiguration conf;

        home.getParent().mkdirsOpt();
        Files.createStoolDirectory(console.verbose, home);
        home.getWorld().resource("templates/maven-settings.xml").copyFile(home.join("maven-settings.xml"));
        conf = new StoolConfiguration(downloadCache());
        conf.shared = shared;
        tuneHostname(conf);
        tuneExplicit(conf);
        Files.createStoolDirectoryOpt(console.verbose, conf.downloadCache).join(STOOL_UPDATE_CHECKED).deleteFileOpt().mkfile();
        conf.save(Session.gson(home.getWorld(), ExtensionsFactory.create(home.getWorld())), home);

        for (String dir : new String[]{"extensions", "backstages", "inbox", "logs", "service-wrapper", "run", "run/users", "tomcat"}) {
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

    private void tuneExplicit(StoolConfiguration conf) {
        boolean error;
        Map<String, Property> properties;
        Property property;

        properties = StoolConfiguration.properties();
        error = false;
        for (Map.Entry<String, String> entry : globalProperties.entrySet()) {
            property = properties.get(entry.getKey());
            if (property == null) {
                console.info.println("property not found: " + entry.getKey());
                error = true;
            } else {
                try {
                    property.set(conf, entry.getValue());
                } catch (Exception e) {
                    console.info.println("invalid value for property " + entry.getKey() + " : " + e.getMessage());
                    e.printStackTrace(console.verbose);
                    error = true;
                }
            }
        }
        if (error) {
            throw new ArgumentException("invalid configuration");
        }
    }

    //-- upgrade

    public void upgrade() throws IOException {
        upgrade_31_32(home);
        upgrade_32_33(home);
    }

    private void upgrade_31_32(FileNode home) throws IOException {
        if (home.join("bin").isDirectory()) {
            console.info.println("upgrading 3.1 -> 3.2: " + home);
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
    }

    private void upgrade_32_33(FileNode home) throws IOException {
        if (home.join("overview.properties").isFile()) {
            console.info.println("upgrading 3.2 -> 3.3");
            exec("mv", home.join("overview.properties").getAbsolute(), home.join("dashboard.properties").getAbsolute());
            ports_32_33(home);
            doUpgrade(stool32_33(), stage32_33());
        }
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
        exec("chown", user, pool.getFile().getAbsolute());
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
