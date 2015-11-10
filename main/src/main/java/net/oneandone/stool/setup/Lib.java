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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.extensions.ExtensionsFactory;
import net.oneandone.stool.stage.ArtifactStage;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Pool;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Diff;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/** Stool's library directory. Lib is an install directory without man and bin. */
public class Lib {
    private final Console console;
    private final FileNode dir;
    private final String group;
    /** json, may be null */
    private final String explicitConfig;

    public Lib(Console console, FileNode dir, String group, String explicitConfig) {
        this.console = console;
        this.dir = dir;
        this.group = group;
        this.explicitConfig = explicitConfig;
    }

    public void create() throws IOException {
        World world;
        Gson gson;
        StoolConfiguration conf;

        world = dir.getWorld();
        gson = Session.gson(world, ExtensionsFactory.create(world));
        Files.createStoolDirectory(console.verbose, dir);
        exec("chgrp", group, dir.getAbsolute());
        // chgrp overwrites the permission - thus, i have to re-set permissions
        exec("chmod", "2775", dir.getAbsolute());

        world.resource("templates/maven-settings.xml").copyFile(dir.join("maven-settings.xml"));
        conf = new StoolConfiguration(downloadCache());
        tuneHostname(conf);
        if (explicitConfig != null) {
            conf = conf.createPatched(gson, explicitConfig);
        }
        conf.save(gson, dir);
        if (!conf.downloadCache.exists()) {
            Files.createStoolDirectory(console.verbose, conf.downloadCache);
        }
        for (String name : new String[]{"extensions", "backstages", "logs", "service-wrapper", "run", "run/users", "tomcat"}) {
            Files.createStoolDirectory(console.verbose, dir.join(name));
        }
        Files.stoolFile(dir.join("run/locks").mkfile());
    }

    private FileNode downloadCache() {
        FileNode directory;

        if (OS.CURRENT == OS.MAC) {
            directory = (FileNode) console.world.getHome().join("Downloads");
            if (directory.isDirectory()) {
                return directory;
            }
        }
        return dir.join("downloads");
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

        file = dir.join("version");
        if (file.isFile()) {
            oldVersion = file.readString();
        } else {
            oldVersion = guessVersion();
        }
        newVersion = JavaSetup.versionObject().toString();
        console.info.println("upgrade " + oldVersion + " -> " + newVersion);
        if (oldVersion.startsWith("3.1.")) {
            upgrade_31_32(dir);
            upgrade_32_33(dir);
        } else if (oldVersion.startsWith("3.2.")) {
            upgrade_32_33(dir);
        } else if (oldVersion.startsWith(("3.3."))) {
            console.info.println("nothing to do");
        } else {
            throw new IOException("don't know how to upgrade " + oldVersion + " -> " + newVersion);
        }
        file.writeString(newVersion);
    }

    private String guessVersion() throws IOException {
        if (dir.join("bin").isDirectory() && !dir.join("bin/lib").isLink()) {
            return "3.1.x";
        }
        if (dir.join("overview.properties").isFile()) {
            return "3.2.x";
        }
        throw new IOException("unknown version of lib directory: " + dir);
    }

    private void upgrade_31_32(FileNode lib) throws IOException {
        exec("mv", lib.join("conf/overview.properties").getAbsolute(), lib.join("overview.properties").getAbsolute());
        exec("sh", "-c", "find . -user servlet | xargs chown stool");
        exec("sh", "-c", "find . -perm 666 | xargs chmod 664");
        exec("sh", "-c", "find . -type d | xargs chmod g+s");
        exec("mv", lib.join("conf").getAbsolute(), lib.join("run").getAbsolute());
        exec("mv", lib.join("wrappers").getAbsolute(), lib.join("backstages").getAbsolute());
        exec("rm", "-rf", lib.join("bin").getAbsolute());
        exec("chgrp", group, ".");
        doUpgrade(stool31_32(), stage31_32());
    }

    private void upgrade_32_33(FileNode lib) throws IOException {
        FileNode tomcat;

        tomcat = lib.join("backstages/overview/shared/run/tomcat.pid");
        if (tomcat.exists()) {
            console.info.println("stopping old overview");
            exec("kill", tomcat.readString().trim());
        }
        exec("rm", "-rf", lib.join("backstages/overview").getAbsolute());
        exec("rm", "-rf", lib.join("overview").getAbsolute());
        exec("mv", lib.join("overview.properties").getAbsolute(), lib.join("dashboard.properties").getAbsolute());

        ports_32_33(lib);
        gavs_32_33(lib);
        doUpgrade(stool32_33(), stage32_33());
    }

    private void gavs_32_33(FileNode lib) throws IOException {
        FileNode backstages;
        FileNode file;
        String str;
        StringBuffer buffer;

        backstages = lib.join("backstages");
        for (FileNode backstage : backstages.list()) {
            file = (FileNode) backstage.join("anchor").resolveLink().join("gav.url");
            if (file.exists()) {
                str = file.readString().trim();
                if (str.contains("@inbox")) {
                    throw new IOException("don't know how to migrate inbox stages: " + str);
                }
                if (str.contains(",")) {
                    buffer = new StringBuffer();
                    for (String item : Separator.COMMA.split(Strings.removeLeft(str, "gav:"))) {
                        if (buffer.length() > 0) {
                            buffer.append(',');
                        }
                        buffer.append("gav:" + item);
                    }
                    str = buffer.toString();
                    file.writeString(str);
                }
                file.move(ArtifactStage.urlFile(file.getParent()));
            }
        }
    }

    private void ports_32_33(FileNode lib) throws IOException {
        FileNode backstages;
        Node ports32;
        Pool pool;

        backstages = lib.join("backstages");
        pool = new Pool(lib.join("run/ports"), 2, Integer.MAX_VALUE, backstages);
        for (Node backstage : backstages.list()) {
            ports32 = backstage.join("ports");
            if (ports32.isFile()) {
                for (Host32 host32 : Host32.load(ports32)) {
                    pool.add(host32.upgrade(backstage));
                }
                ports32.deleteFile();
            }
        }
        pool.save();
    }

    private static Lib upgradeLib = null;
    private static FileNode upgradeBackstage = null;

    private void doUpgrade(Upgrade stoolMapper, Upgrade stageMapper) throws IOException {
        upgradeLib = this;
        doUpgradeStool(stoolMapper);
        for (FileNode oldBackstage : dir.join("backstages").list()) {
            // TODO
            upgradeBackstage = oldBackstage;
            transform(oldBackstage.join("config.json"), stageMapper);
        }
    }

    private void doUpgradeStool(Upgrade stoolMapper) throws IOException {
        transform(dir.join("config.json"), stoolMapper);
    }

    private void transform(FileNode json, Upgrade mapper) throws IOException {
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
        Files.exec(console.info, dir, cmd);
    }

    //--

    public static Upgrade stool31_32() {
        return new Upgrade() {
        };
    }

    public static Upgrade stage31_32() {
        return new Upgrade() {
        };
    }

    public static Upgrade stool32_33() {
        return new Upgrade() {
            void portOverviewRemove() {
            }
            void errorToolRemove() {
            }
            void updateIntervalRemove() {
            }
            JsonElement macrosTransform(JsonElement map) throws IOException {
                String opts;

                opts = upgradeLib.proxyOpts();
                if (opts != null) {
                    ((JsonObject) map).add("proxyOpts", new JsonPrimitive(opts));
                }
                return map;
            }
        };
    }

    public Upgrade stage32_33() {
        return new Upgrade() {
            JsonElement tomcatEnvTransform(JsonElement e) throws IOException {
                return toTomcatEnvMap((JsonArray) e, upgradeBackstage.getOwner().toString());
            }
            String suffixRename() {
                return "suffixes";
            }
            JsonElement suffixTransform(JsonElement e) {
                JsonArray result;

                result = new JsonArray();
                if (!e.getAsString().isEmpty()) {
                    result.add(e);
                }
                return result;
            }
        };
    }

    private JsonObject toTomcatEnvMap(JsonArray array, String user) throws IOException {
        Environment env;
        JsonObject result;
        String name;
        String value;

        env = getenv(user);
        result = new JsonObject();
        for (JsonElement element : array) {
            name = element.getAsString();
            if (name.equals("USER") || name.equals("HOME")) {
                continue;
            }
            value = env.get(name);
            if (value == null) {
                throw new IOException("user '" + user + "' has no environment variable " + name);
            }
            result.add(name, new JsonPrimitive(value));
        }
        return result;
    }

    private String proxyOpts() throws IOException {
        Environment env;
        String result;

        for (FileNode backstage : dir.join("backstages").list()) {
            env = getenv(backstage.getOwner().toString());
            result = env.proxyOpts();
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private Environment getenv(String user) throws IOException {
        Launcher launcher;
        StringWriter out;
        StringWriter err;
        String errString;
        Environment result;
        int idx;

        launcher = new Launcher(dir, "sudo", "-i", "-u", user, "env");
        out = new StringWriter();
        err = new StringWriter();
        launcher.exec(out, err);
        errString = err.toString();
        if (!errString.isEmpty()) {
            console.verbose.println("ignoring error output in environment of user " + user + ":");
            console.verbose.println(err.toString());
        }
        result = new Environment();
        for (String line : Separator.RAW_LINE.split(out.toString())) {
            line = line.trim();
            if (!line.isEmpty()) {
                idx = line.indexOf('=');
                if (idx == -1) {
                    console.info.println("ignoring strange environment line of user " + user + ":");
                    console.info.println(line);
                } else {
                    result.set(line.substring(0, idx), line.substring(idx + 1));
                }
            }
        }
        return result;
    }
}
