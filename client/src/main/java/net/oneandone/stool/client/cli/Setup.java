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

import com.google.gson.Gson;
import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Server;
import net.oneandone.stool.client.Configuration;
import net.oneandone.sushi.fs.ExistsException;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Setup {
    public static FileNode cisotools(World world) {
        String path;

        path = System.getenv("CISOTOOLS_HOME");
        return path == null ? null : world.file(path);
    }

    private final World world;
    private final Gson gson;
    private final FileNode home;
    private final Console console;
    private final String version;
    private final boolean batch;
    private final String nameAndHost;
    private final String registryPrefix;

    public Setup(Globals globals, String nameAndHost, boolean batch, String registryPrefix) {
        this.world = globals.getWorld();
        this.gson = globals.getGson();
        this.home = globals.getHome();
        this.console = globals.getConsole();
        this.version = Main.versionString(world);
        this.batch = batch;
        this.nameAndHost = nameAndHost;
        this.registryPrefix = registryPrefix;
    }

    public void run() throws IOException {
        if (home.isDirectory()) {
            console.info.println("Stool is already set up in " + home.getAbsolute() + ", updating servers only.");
            updateServers();
        } else {
            console.info.println("Stool " + version + " setup in " + home);
            console.info.println();

            create();
        }
    }

    private void updateServers() throws IOException {
        Configuration configuration;

        if (batch) {
            throw new ArgumentException("-batch is not supported in update mode");
        }
        if (nameAndHost != null) { // TODO: why?
            throw new ArgumentException("local is not supported in update mode");
        }
        configuration = updateConfiguration();
        configuration = select(configuration, true);
        console.info.println();
        console.readline("Press return to update servers, ctrl-c to abort");
        configuration.save(gson);
        console.info.println("done");
    }

    private void create() throws IOException {
        Configuration configuration;
        String inc;

        configuration = createConfiguration();
        if (!batch) {
            console.info.println();
            console.info.println("Ready to create Stool home directory: " + home.getAbsolute());
            console.pressReturn();
        }
        console.info.println("Creating " + home);
        doCreate(configuration);
        inc = home.join("shell.inc").getAbsolute();
        console.info.println("Done.");
        console.info.println();
        console.info.println("If you want bash completion and a stage indicator in your shell prompt: ");
        console.info.println("  Make sure to source " + inc + " in your shell profile");
        console.info.println("  (e.g. with 'echo \". " + inc + "\" >> ~/.bash_profile')");
        console.info.println("  Don't forget to restart your terminal.");
        if (configuration.needAuthentication()) {
            console.info.println("At least one of the servers you're using needs authentication. Please run");
            console.info.println("  stool auth");
            console.info.println("once to do so.");
        }
    }

    private Boolean yesNo(String str) {
        String answer;

        while (true) {
            answer = console.readline(str);
            answer = answer.toLowerCase();
            switch (answer) {
                case "y":
                    return true;
                case "n":
                    return false;
                case "":
                    return null;
                default:
                    console.info.println("invalid answer: " + answer);
            }
        }
    }

    private Configuration createConfiguration() throws IOException {
        Configuration result;

        result = readConfiguration();
        if (!batch && !result.isEmpty()) {
            result = select(result, false);
        } else {
            result = result.newEnabled();
        }
        return result;
    }

    private Configuration select(Configuration configuration, boolean dflt) {
        Configuration result;
        Boolean enable;
        String yesNo;

        result = new Configuration(configuration.file);
        result.setRegistryPrefix(configuration.registryPrefix());
        console.info.println("Stages are hosted on servers. Please choose the servers you want to use:");
        for (Server server : configuration.allServer()) {
            if (dflt) {
                yesNo = server.enabled ? "Y/n" : "y/N";
            } else {
                yesNo = "y/n";
            }
            while (true) {
                enable = yesNo("  " + server.name + " (" + server.url + ") [" + yesNo + "]? ");
                if (enable == null) {
                    if (dflt) {
                        enable = server.enabled;
                        break;
                    }
                } else {
                    break;
                }
            }
            server.withEnabled(enable).addTo(result);
        }
        return result;
    }

    private Configuration updateConfiguration() throws IOException {
        Configuration result;
        Configuration add;
        FileNode additional;

        result = new Configuration(home.join("client.json"));
        result.load();

        additional = cisotoolsEnvironment();
        if (additional != null) {
            add = new Configuration(additional);
            add.load();
            result.setRegistryPrefix(add.registryPrefix());
            for (Server s : add.allServer()) {
                if (result.serverLookup(s.name) == null) {
                    s.withEnabled(false).addTo(result);
                    // default to disabled, because hitting return for all servers must not change anything
                }
            }
        }
        return result;
    }

    private Configuration readConfiguration() throws IOException {
        FileNode file;
        Configuration manager;
        int idx;

        if (nameAndHost != null) {
            file = null;
        } else {
            file = cisotoolsEnvironment();
        }
        manager = new Configuration(file);
        if (file != null) {
            manager.load();
        } else {
            idx = nameAndHost.indexOf('=');
            if (idx == -1) {
                throw new IllegalStateException("missing '=': " + nameAndHost);
            }
            manager.add(nameAndHost.substring(0, idx), true, nameAndHost.substring(idx + 1), null);
        }
        return manager;
    }

    public void doCreate(Configuration environment) throws IOException {
        Configuration configuration;

        home.mkdir();
        world.resource("files/home").copyDirectory(home);
        configuration = new Configuration(home.join("client.json"));
        for (Server s : environment.allServer()) {
            s.addTo(configuration);
        }
        configuration.setRegistryPrefix(registryPrefix != null ? registryPrefix : environment.registryPrefix());
        configuration.setVersion(version);
        configuration.save(gson);
    }

    private FileNode cisotoolsEnvironment() throws FileNotFoundException, ExistsException {
        FileNode cisotools;

        cisotools = cisotools(world);
        return cisotools == null ? null : cisotools.join("stool/environment-6.json").checkFile();
    }
}
