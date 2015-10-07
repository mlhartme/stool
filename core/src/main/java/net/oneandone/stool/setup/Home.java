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

import java.io.IOException;
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

}
