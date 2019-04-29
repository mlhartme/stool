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
package net.oneandone.stool.client;

import net.oneandone.stool.client.cli.Main;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * Stool home directory. In unix file system hierarchy this comes close to the lib directory - although it contains
 * etc stuff (config.json) and log files.
 */
public class Home {
    public static void create(FileNode home) throws IOException {
        Home obj;

        home.checkNotExists();
        obj = new Home(home);
        obj.create();
    }

    private final FileNode dir;
    private final Map<String, String> opts;

    public Home(FileNode dir) {
        this.dir = dir;
        this.opts = new HashMap<>();
    }

    public void addOpts(Map<String, String> args) {
        opts.putAll(args);
    }

    public void addIfNew(String name, String value) {
        if (!opts.containsKey(name)) {
            opts.put(name, value);
        }
    }

    public void create() throws IOException {
        World world;

        dir.mkdir();
        world = dir.getWorld();
        world.resource("files/home").copyDirectory(dir);
        serverDir().mkdir();
        dir.join("server.yml").writeString(serverYml());
        versionFile().writeString(Main.versionString(world));
    }

    public String version() throws IOException {
        return versionFile().readString().trim();
    }

    public String serverYml() throws IOException {
        StringBuilder builder;
        String serverHome;
        String cisoTools;

        builder = new StringBuilder();
        serverHome = serverDir().getAbsolute();
        cisoTools = System.getenv("CISOTOOLS_HOME");
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
        builder.append("      - \"8000:8000\"\n");
        builder.append("    environment:\n");
        builder.append("      - \"SERVER_HOME=" + serverHome + "\"\n");
        builder.append("      - \"HOSTNAME=" + hostname() + "\"\n");

        for (Map.Entry<String, String> entry : opts.entrySet()) {
            builder.append("      - \"" + entry.getKey() + "=" + entry.getValue() + "\"\n");
        }
        builder.append("    volumes:\n");
        builder.append("      - \"/var/run/docker.sock:/var/run/docker.sock\"\n");
        builder.append("      - \"" + serverHome + ":/var/lib/stool\"\n");
        if (cisoTools != null) {
            builder.append("      - \"" + dir.getWorld().file(cisoTools).join("stool/templates-5").checkDirectory().getAbsolute() + ":/var/lib/stool/templates\"\n");
        }
        return builder.toString();
    }

    private static String hostname() throws UnknownHostException {
        return InetAddress.getLocalHost().getCanonicalHostName();
    }

    public FileNode serverDir() {
        return dir.join("server");
    }

    public FileNode versionFile() {
        return dir.join("version");
    }
}
