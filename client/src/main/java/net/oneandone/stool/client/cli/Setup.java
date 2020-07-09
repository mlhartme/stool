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
import java.net.URI;
import java.util.Properties;

public class Setup {
    private static final String LOCALHOST = "localhost"; // TODO

    private final World world;
    private final Gson gson;
    private final FileNode home;
    private final Console console;
    private final String version;
    private final boolean batch;
    private final boolean local;

    private final URI portus;
    private final String portusPrefix;

    public Setup(Globals globals, boolean batch, boolean local) {
        int idx;
        Properties tmp;

        this.world = globals.getWorld();
        this.gson = globals.getGson();
        this.home = globals.getHome();
        this.console = globals.getConsole();
        this.version = Main.versionString(world);
        this.batch = batch;
        this.local = local;
        try {
            // TODO
            tmp = world.getHome().join(".sc.properties").readProperties();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        portus = URI.create(tmp.getProperty("portus") + (local ? LOCALHOST + "/" : "waterloo/")); // TODO
        portusPrefix = portus.getHost() + portus.getPath();
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
        Configuration environment;

        if (batch) {
            throw new ArgumentException("-batch is not supported in update mode");
        }
        if (local) { // TODO: why?
            throw new ArgumentException("local is not supported in update mode");
        }
        environment = updateEnvironment();
        environment = select(environment, true);
        console.info.println();
        console.readline("Press return to update servers, ctrl-c to abort");
        environment.save(gson);
        console.info.println("done");
    }

    private void create() throws IOException {
        Configuration environment;
        String inc;

        environment = createEnvironment();
        if (!batch) {
            console.info.println();
            console.info.println("Ready to create Stool home directory: " + home.getAbsolute());
            console.pressReturn();
        }
        console.info.println("Creating " + home);
        doCreate(environment);
        inc = home.join("shell.inc").getAbsolute();
        console.info.println("Done.");
        console.info.println();
        console.info.println("If you want bash completion and a stage indicator in your shell prompt: ");
        console.info.println("  Make sure to source " + inc + " in your shell profile");
        console.info.println("  (e.g. with 'echo \". " + inc + "\" >> ~/.bash_profile')");
        console.info.println("  Don't forget to restart your terminal.");

        if (environment.serverLookup("localhost") != null) {
            console.info.println();
            console.info.println("You've enabled a local Stool server to host stages - start/stop it like this:");
            console.info.println("  kubectl apply -f " + home.join("server.yaml").getAbsolute());
            console.info.println("  kubectl delete -f " + home.join("server.yaml").getAbsolute());
        }
        if (environment.needAuthentication()) {
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

    private Configuration createEnvironment() throws IOException {
        Configuration result;

        result = readEnvironment();
        if (!batch && !result.isEmpty()) {
            result = select(result, false);
        } else {
            result = result.newEnabled();
        }
        return result;
    }

    private Configuration select(Configuration environment, boolean dflt) {
        Configuration result;
        Boolean enable;
        String yesNo;

        result = new Configuration(environment.file);
        console.info.println("Stages are hosted on servers. Please choose the servers you want to use:");
        for (Server server : environment.allServer()) {
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

    private Configuration updateEnvironment() throws IOException {
        Configuration result;
        Configuration add;
        FileNode additional;

        result = new Configuration(home.join("client.json"));
        result.load();

        additional = cisotoolsEnvironment();
        if (additional != null) {
            add = new Configuration(additional);
            add.load();
            for (Server s : add.allServer()) {
                if (result.serverLookup(s.name) == null) {
                    s.withEnabled(false).addTo(result);
                    // default to disabled, because hitting return for all servers must not change anything
                }
            }
        }
        return result;
    }

    private Configuration readEnvironment() throws IOException {
        FileNode file;
        Configuration manager;

        if (local) {
            file = null;
        } else {
            file = cisotoolsEnvironment();
        }
        manager = new Configuration(file);
        if (file != null) {
            manager.load();
        } else {
            manager.add("localhost", true, "http://" + LOCALHOST + ":" + serverPort() + "/api", null);
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

        configuration.setRegistryPrefix(portusPrefix);
        configuration.save(gson);
        versionFile().writeString(Main.versionString(world));
    }

    private int serverPort() {
        return 31000;
    }

    public String version() throws IOException {
        return versionFile().readString().trim();
    }

    private FileNode cisotoolsEnvironment() throws FileNotFoundException, ExistsException {
        FileNode cisotools;

        cisotools = cisotools();
        return cisotools == null ? null : cisotools.join("stool/environment-6.json").checkFile();
    }

    private FileNode cisotools() {
        String path;

        path = System.getenv("CISOTOOLS_HOME");
        return path == null ? null : world.file(path);
    }

    public FileNode versionFile() {
        return home.join("version");
    }
}
