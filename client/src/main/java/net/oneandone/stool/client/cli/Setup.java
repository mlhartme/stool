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
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Setup {
    private final World world;
    private final Gson gson;
    private final FileNode home;
    private final Console console;
    private final String version;
    private final boolean batch;
    private final Boolean explicitServer; // null to ask
    private final FileNode explicitEnvironment;
    private final Map<String, String> opts;

    public Setup(Globals globals, boolean batch, Boolean server, FileNode environment, List<String> opts) {
        int idx;

        if (batch && server == null) {
            throw new ArgumentException("cannot ask about server in batch mode");
        }
        this.world = globals.getWorld();
        this.gson = globals.getGson();
        this.home = globals.getHome();
        this.console = globals.getConsole();
        this.version = Main.versionString(world);
        this.batch = batch;
        this.explicitServer = server;
        this.explicitEnvironment = environment;
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
        console.info.println("Stool " + version + " setup");
        console.info.println();

        if (home.isDirectory()) {
            throw new IOException("TODO: upgrade");
        } else {
            create();
        }
    }

    private void create() throws IOException {
        boolean server;
        ServerManager environment;
        String inc;

        environment = environment();
        server = explicitServer == null ? askServer() : explicitServer;
        if (!batch) {
            console.info.println();
            console.info.println("Ready to create Stool home directory: " + home.getAbsolute());
            console.pressReturn();
        }
        console.info.println("Creating " + home);
        doCreate(environment, server);
        inc = home.join("shell.inc").getAbsolute();
        console.info.println("Done.");
        console.info.println();
        console.info.println("If you want bash completion and a stage indicator in your shell prompt: ");
        console.info.println("  Make sure to source " + inc + " in your shell profile");
        console.info.println("  (e.g. with 'echo \". " + inc + "\" >> ~/.bash_profile')");
        console.info.println("  Don't forget to restart your terminal.");
        if (server) {
            console.info.println();
            console.info.println("A local Stool server has been setup - start with ");
            console.info.println("    docker-compose -f " + home.join("server.yml").getAbsolute() + " up -d");
            console.info.println("Check logs with ");
            console.info.println("    docker-compose -f " + home.join("server.yml").getAbsolute() + " logs");
        }
    }

    private boolean askServer() {
        console.info.println("Local server");
        console.info.println("  You need a local server if you want to run stages on this machine.");
        console.info.println("  (Note that a local server requires Docker (with docker-compose) installed on this machine.)");
        return yesNo("    Setup local server [y/n)]? ");
    }

    private boolean yesNo(String str) {
        String answer;

        while (true) {
            answer = console.readline(str);
            answer = answer.toLowerCase();
            switch (answer) {
                case "y":
                    return true;
                case "n":
                    return false;
                default:
                    console.info.println("invalid answer: " + answer);
            }
        }
    }

    private ServerManager environment() throws IOException {
        ServerManager result;

        result = readEnvironment();
        if (!batch && !result.isEmpty()) {
            result = select(result);
        }
        return result;
    }

    private ServerManager select(ServerManager environment) {
        ServerManager result;

        result = new ServerManager(null);
        console.info.println("Remote servers");
        console.info.println("  Stages are hosted on servers. Please choose the remote servers you want to use:");
        for (Server server : environment) {
            if (yesNo("    " + server.name + " (" + server.url +  ") [y/n]? ")) {
                server.addTo(result);
            }
        }
        return result;
    }

    private ServerManager readEnvironment() throws IOException {
        FileNode file;
        ServerManager manager;

        file = explicitEnvironment != null ? explicitEnvironment : implicitEnvironment();
        manager = new ServerManager(file);
        if (file.exists()) {
            manager.load();
        }
        return manager;
    }

    private FileNode implicitEnvironment() {
        FileNode cisotools;

        cisotools = cisotools();
        return cisotools != null ? cisotools.join("stool/environment.json") : world.locateClasspathEntry(getClass()).join("environment.json");
    }

    //--

    public void addIfNew(String name, String value) {
        if (!opts.containsKey(name)) {
            opts.put(name, value);
        }
    }

    public void doCreate(ServerManager envinronmnt, boolean server) throws IOException {
        ServerManager manager;

        home.mkdir();
        world.resource("files/home").copyDirectory(home);
        manager = new ServerManager(home.join("servers.json"));
        if (server) {
            manager.add("localhost", "http://localhost:" + port() + "/api", null);
        }
        for (Server s : envinronmnt) {
            s.addTo(manager);
        }
        manager.save(gson);
        serverDir().mkdir();
        home.join("server.yml").writeString(serverYml());
        versionFile().writeString(Main.versionString(world));
    }

    private String port() {
        return opts.getOrDefault("PORT_FIRST", "9000");
    }

    public String version() throws IOException {
        return versionFile().readString().trim();
    }

    public String serverYml() throws IOException {
        StringBuilder builder;
        String serverHome;
        String dockerHost;
        FileNode cisotools;
        String port;
        String portNext;
        String portNextNext;

        builder = new StringBuilder();
        serverHome = serverDir().getAbsolute();
        dockerHost = hostname();
        cisotools = cisotools();
        port = port();
        portNext = Integer.toString(Integer.parseInt(port) + 1);
        portNextNext = Integer.toString(Integer.parseInt(port) + 2);
        addIfNew("VHOSTS", Boolean.toString(hasDnsStar(dockerHost)));
        addIfNew("LOGLEVEL", "INFO"); // for documentation purpose
        if (cisotools != null) {
            addIfNew("REGISTRY_NAMESPACE", "contargo.server.lan/mhm");
            addIfNew("LDAP_UNIT", "cisostages");
            addIfNew("ADMIN", "michael.hartmeier@ionos.com");
            addIfNew("MAIL_HOST", "mri.server.lan");
            addIfNew("JMX_USAGE", "jconsole -J-Djava.class.path=$CISOTOOLS_HOME/stool/opendmk_jmxremote_optional_jar-1.0-b01-ea.jar service:jmx:jmxmp://%s");
        }
        builder.append("version: '3'\n");  // I started with '3.7', but Ubuntu 16.4 packages just have 3.2 ...
        builder.append("services:\n");
        builder.append("  stool-server:\n");
        builder.append("    image: contargo.server.lan/cisoops-public/stool-server\n");
        builder.append("    ports:\n");
        builder.append("      - " + port + ":" + port + "\n");
        builder.append("      - " + portNext + ":" + portNext + "\n");
        builder.append("      - " + portNextNext + ":" + 9875 + "\n");
        builder.append("    environment:\n");
        builder.append("      - DOCKER_HOST=" + dockerHost + "\n");
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
        return builder.toString();
    }

    private FileNode cisotools() {
        String path;

        path = System.getenv("CISOTOOLS_HOME");
        return path == null ? null : world.file(path);
    }

    private static String hostname() throws UnknownHostException {
        return InetAddress.getLocalHost().getCanonicalHostName();
    }

    public FileNode serverDir() {
        return home.join("server");
    }

    public FileNode versionFile() {
        return home.join("version");
    }

    private boolean hasDnsStar(String hostname) throws IOException {
        String ip;

        ip = digIp(hostname);
        if (ip.isEmpty()) {
            return false; // no dns entry at all
        }
        return digIp("subdomain." + hostname).equals(ip);
    }

    private String digIp(String name) throws IOException {
        return home.exec("dig", "+short", name).trim();
    }
}