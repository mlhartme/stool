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

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.client.Globals;
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
    private final FileNode home;
    private final Console console;
    private final String version;
    private final boolean batch;
    private final Map<String, String> opts;

    public Setup(Globals globals, boolean batch, List<String> opts) {
        int idx;

        this.world = globals.getWorld();
        this.home = globals.getHome();
        this.console = globals.getConsole();
        this.version = Main.versionString(world);
        this.batch = batch;
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
        console.info.println("Stool " + version);

        if (home.isDirectory()) {
            update();
        } else {
            create();
        }
    }

    private void create() throws IOException {
        if (!batch) {
            console.info.println("Ready to create home directory: " + home.getAbsolute());
            console.pressReturn();
        }
        console.info.println("Creating " + home);
        doCreate();
        console.info.println("Done.");
        console.info.println("Make sure to add " + home.join("shell.inc") + " to your shell profile (e.g. ~/.bash_profile) and restart your terminal.");
        console.info.println("Note: to start a local server: install Docker and run");
        console.info.println("    docker-compose -f " + home.join("server.yml").getAbsolute() + " up");
    }

    private void update() throws IOException {
        throw new IOException("TODO: upgrade");
    }

    //--

    public void addIfNew(String name, String value) {
        if (!opts.containsKey(name)) {
            opts.put(name, value);
        }
    }

    public void doCreate() throws IOException {
        ServerManager manager;

        home.mkdir();
        world.resource("files/home").copyDirectory(home);
        manager = new ServerManager(home.join("servers"), null, "", "");
        manager.add("localhost", "http://localhost:" + port() + "/api");
        manager.add("gem1", "http://gem1.ciso.server.lan:8000/api");
        manager.save();
        serverDir().mkdir();
        home.join("server.yml").writeString(serverYml());
        versionFile().writeString(Main.versionString(world));
    }

    private String port() {
        return opts.getOrDefault("PORT_FIRST", "8000");
    }

    public String version() throws IOException {
        return versionFile().readString().trim();
    }

    public String serverYml() throws IOException {
        StringBuilder builder;
        String serverHome;
        String dockerHost;
        String cisoTools;
        String port;

        builder = new StringBuilder();
        serverHome = serverDir().getAbsolute();
        dockerHost = hostname();
        cisoTools = System.getenv("CISOTOOLS_HOME");
        port = port();
        addIfNew("VHOSTS", Boolean.toString(hasDnsStar(dockerHost)));
        if (cisoTools != null) {
            addIfNew("REGISTRY_NAMESPACE", "contargo.server.lan/mhm");
            addIfNew("LDAP_UNIT", "cisostages");
            addIfNew("ADMIN", "michael.hartmeier@ionos.com");
            addIfNew("MAIL_HOST", "mri.server.lan");
        }
        builder.append("version: '3.7'\n");
        builder.append("services:\n");
        builder.append("  stool-server:\n");
        builder.append("    image: \"contargo.server.lan/cisoops-public/stool-server\"\n");
        builder.append("    ports:\n");
        builder.append("      - \"" + port + ":" + port + "\"\n");
        builder.append("    environment:\n");
        builder.append("      - \"SERVER_HOME=" + serverHome + "\"\n");
        builder.append("      - \"DOCKER_HOST=" + dockerHost + "\"\n");

        for (Map.Entry<String, String> entry : opts.entrySet()) {
            builder.append("      - \"" + entry.getKey() + "=" + entry.getValue() + "\"\n");
        }
        builder.append("    volumes:\n");
        builder.append("      - \"/var/run/docker.sock:/var/run/docker.sock\"\n");
        builder.append("      - \"" + serverHome + ":/var/lib/stool\"\n");
        if (cisoTools != null) {
            builder.append("      - \"" + world.file(cisoTools).join("stool/templates-5").checkDirectory().getAbsolute() + ":/var/lib/stool/templates\"\n");
        }
        return builder.toString();
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