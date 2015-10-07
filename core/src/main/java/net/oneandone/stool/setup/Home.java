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
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.util.Diff;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

public class Home {
    public static final String STOOL_UPDATE_CHECKED = ".stool.update.checked";

    private final Console console;
    private final FileNode home;
    private final boolean shared;
    private final Map<String, String> globalProperties;

    public Home(Console console, FileNode home, boolean shared, Map<String, String> globalProperties) {
        this.console = console;
        this.home = home;
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

    //-- Mapper Code

    public void upgrade() throws IOException {
        upgrade_31_32(console.info, home);
        upgrade_32_33(console.info, home);
    }

    private void upgrade_31_32(PrintWriter log, FileNode home) throws IOException {
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
            upgradeJson(stool31_32(), stage31_32());
        }
    }

    private void upgrade_32_33(PrintWriter log, FileNode home) throws IOException {
        if (home.join("overview.properties").isFile()) {
            log.println("migrating 3.2 -> 3.3");
            Files.exec(log, home, "mv", home.join("overview.properties").getAbsolute(), home.join("dashboard.properties").getAbsolute());
            upgradeJson(stool32_33(), stage32_33());
        }
    }

    private void upgradeJson(Object stoolMapper, Object stageMapper) throws IOException {
        String current;
        final String path = "config.json";
        final FileNode dest;
        final String result;
        String diff;

        dest = home.join(path);
        current = dest.readString();
        /*
        result = Transform.mergeConfig(oldHome.join(path).readString(), current, stool30_31());
        diff = Diff.diff(current, result);
        return new Patch("M " + dest.getAbsolute(), diff) {
            public void apply() throws IOException {
                dest.writeString(result);
            }
        };
*/
    }

    //--

    private static Object stool31_32() {
        return new Object() {
        };
    }

    private static Object stage31_32() {
        return new Object() {
        };
    }

    private static Object stool32_33() {
        return new Object() {
        };
    }

    private static Object stage32_33() {
        return new Object() {
        };
    }
}
