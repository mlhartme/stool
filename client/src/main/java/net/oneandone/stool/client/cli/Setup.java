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
import net.oneandone.stool.client.ServerManager;
import net.oneandone.sushi.fs.ExistsException;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Setup {
    private static final String LOCALHOST = "localhost";

    private final World world;
    private final Gson gson;
    private final FileNode home;
    private final Console console;
    private final String version;
    private final boolean batch;
    private final boolean local;
    private final Map<String, String> opts;

    public Setup(Globals globals, boolean batch, boolean local, List<String> opts) {
        int idx;

        this.world = globals.getWorld();
        this.gson = globals.getGson();
        this.home = globals.getHome();
        this.console = globals.getConsole();
        this.version = Main.versionString(world);
        this.batch = batch;
        this.local = local;
        this.opts = new HashMap<>();
        for (String opt : opts) {
            idx = opt.indexOf('=');
            if (idx == -1) {
                throw new ArgumentException("invalid option: " + opt);
            }
            this.opts.put(opt.substring(0, idx), opt.substring(idx + 1));
        }
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
        ServerManager environment;

        if (batch) {
            throw new ArgumentException("-batch is not supported in update mode");
        }
        if (local) {
            throw new ArgumentException("-local is not supported in update mode");
        }
        environment = updateEnvironment();
        environment = select(environment, true);
        console.info.println();
        console.readline("Press return to update servers, ctrl-c to abort");
        environment.save(gson);
        console.info.println("done");
    }

    private void create() throws IOException {
        ServerManager environment;
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

        if (environment.lookup("localhost") != null) {
            console.info.println();
            console.info.println("You've enabled a local Stool server to host stages - run it like this:");
            console.info.println("  docker network create stool");
            console.info.println("  alias sserver=\"docker-compose -f " +  home.join("server.yml").getAbsolute() + "\"");
            console.info.println("  sserver up -d");
            console.info.println("  sserver logs");
            console.info.println("  sserver down");
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

    private ServerManager createEnvironment() throws IOException {
        ServerManager result;

        result = readEnvironment();
        if (!batch && !result.isEmpty()) {
            result = select(result, false);
        } else {
            result = result.newEnabled();
        }
        return result;
    }

    private ServerManager select(ServerManager environment, boolean dflt) {
        ServerManager result;
        Boolean enable;
        String yesNo;

        result = new ServerManager(environment.file);
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

    private ServerManager updateEnvironment() throws IOException {
        ServerManager result;
        ServerManager add;
        FileNode additional;

        result =  new ServerManager(home.join("servers.json"));
        result.load();

        additional = cisotoolsEnvironment();
        if (additional != null) {
            add = new ServerManager(additional);
            add.load();
            for (Server s : add.allServer()) {
                if (result.lookup(s.name) == null) {
                    s.withEnabled(false).addTo(result);  // default to disabled, because hitting return for all servers must not change anything
                }
            }
        }
        return result;
    }

    private ServerManager readEnvironment() throws IOException {
        FileNode file;
        ServerManager manager;

        if (local) {
            file = null;
        } else {
            file = cisotoolsEnvironment();
        }
        manager = new ServerManager(file);
        if (file != null) {
            manager.load();
        } else {
            manager.add("localhost", true, "http://" + LOCALHOST + ":" + port() + "/api", null);
        }
        return manager;
    }

    //--

    public void addIfNew(String name, String value) {
        if (!opts.containsKey(name)) {
            opts.put(name, value);
        }
    }

    public void doCreate(ServerManager environment) throws IOException {
        ServerManager manager;

        home.mkdir();
        world.resource("files/home").copyDirectory(home);
        manager = new ServerManager(home.join("servers.json"));
        for (Server s : environment.allServer()) {
            s.addTo(manager);
        }
        manager.save(gson);
        serverDir().mkdir();
        home.join("server.yml").writeString(serverYaml());
        versionFile().writeString(Main.versionString(world));
    }

    private String port() {
        return opts.getOrDefault("PORT_FIRST", "9000");
    }

    public String version() throws IOException {
        return versionFile().readString().trim();
    }

    public String serverYaml() throws IOException {
        StringBuilder builder;
        String serverHome;
        FileNode cisotools;
        String port;
        String portNext;
        String portNextNext;

        builder = new StringBuilder();
        serverHome = serverDir().getAbsolute();
        cisotools = cisotools();
        port = port();
        portNext = Integer.toString(Integer.parseInt(port) + 1);
        portNextNext = Integer.toString(Integer.parseInt(port) + 2);
        addIfNew("VHOSTS", Boolean.toString(hasDnsStar()));
        addIfNew("LOGLEVEL", "INFO"); // for documentation purpose
        addIfNew("ENGINE_LOG", "false"); // for engine wire logs
        if (cisotools != null) {
            addIfNew("REGISTRY_NAMESPACE", "contargo.server.lan/mhm");
            addIfNew("LDAP_UNIT", "cisostages");
            addIfNew("ADMIN", "michael.hartmeier@ionos.com");
            addIfNew("JMX_USAGE", "jconsole -J-Djava.class.path=$CISOTOOLS_HOME/stool/opendmk_jmxremote_optional_jar-1.0-b01-ea.jar service:jmx:jmxmp://localhost:%d");
        }
        builder.append("version: '3'\n");  // I started with '3.7', but Ubuntu 16.4 packages just have 3.2 ...
        builder.append("services:\n");
        builder.append("  stool-server:\n");
        builder.append("    image: contargo.server.lan/cisoops-public/stool-server\n");
        builder.append("    ports:\n");

        builder.append("      - " + hostip(LOCALHOST) + ":" + port + ":" + port + "\n");
        // bind to 127.0.0.1 to forbid access from other machines
        builder.append("      - 127.0.0.1:" + portNext + ":" + portNext + "\n");
        builder.append("      - 127.0.0.1:" + portNextNext + ":" + 9875 + "\n");
        builder.append("    environment:\n");
        builder.append("      - DOCKER_HOST=" + LOCALHOST + "\n");
        builder.append("      - OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,quiet=n,address=" + portNext + "\n");

        for (Map.Entry<String, String> entry : opts.entrySet()) {
            builder.append("      - " + entry.getKey() + "=" + entry.getValue() + "\n");
        }
        builder.append("    volumes:\n");
        builder.append("      - /var/run/docker.sock:/var/run/docker.sock:rw\n");
        builder.append("      - " + serverHome + ":/var/lib/stool:rw\n");
        builder.append("      - " + world.getHome().join(".fault").getAbsolute() + ":" + "/etc/fault/workspace:ro\n");
        if (cisotools != null) {
            builder.append("      - " + cisotools.join("stool/templates-5").checkDirectory().getAbsolute() + ":/var/lib/stool/templates:ro\n");
        }
        builder.append("networks:\n");
        builder.append("  default:\n");
        builder.append("    external:\n");
        builder.append("      name: stool");
        return builder.toString();
    }

    private FileNode cisotoolsEnvironment() throws FileNotFoundException, ExistsException {
        FileNode cisotools;

        cisotools = cisotools();
        return cisotools == null ? null : cisotools.join("stool/environment.json").checkFile();
    }

    private FileNode cisotools() {
        String path;

        path = System.getenv("CISOTOOLS_HOME");
        return path == null ? null : world.file(path);
    }

    private static String hostip(String name) throws UnknownHostException {
        return InetAddress.getByName(name).getHostAddress();
    }

    public FileNode serverDir() {
        return home.join("server");
    }

    public FileNode versionFile() {
        return home.join("version");
    }

    private boolean hasDnsStar() throws UnknownHostException {
        String ip;

        ip = hostip(LOCALHOST);
        if (ip.isEmpty()) {
            return false; // no dns entry at all
        }
        try {
            return hostip("subdomain." + LOCALHOST).equals(ip);
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
