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
import net.oneandone.sushi.util.Substitution;
import net.oneandone.sushi.util.SubstitutionException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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

    private final Properties todo;

    public Setup(Globals globals, boolean batch, boolean local, List<String> opts) {
        int idx;
        String path;

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

        path = System.getProperty("test.properties");
        if (path == null) {
            todo = null;
        } else {
            try {
                todo = world.file(path).readProperties();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
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
        Configuration environment;

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
                    s.withEnabled(false).addTo(result);  // default to disabled, because hitting return for all servers must not change anything
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
            manager.add("localhost", true, "http://" + LOCALHOST + ":" + port() + "/api", null);
        }
        return manager;
    }

    //--

    public static void addIfNew(Map<String, String> env, String name, String value) {
        if (!env.containsKey(name)) {
            env.put(name, value);
        }
    }

    public void doCreate(Configuration environment) throws IOException {
        Configuration configuration;

        home.mkdir();
        world.resource("files/home").copyDirectory(home);
        configuration = new Configuration(home.join("client.json"));
        for (Server s : environment.allServer()) {
            s.addTo(configuration);
        }

        // TODO
        if (todo != null) {
            configuration.setRegistryPrefix(todo.getProperty("prefix"));
        }

        configuration.save(gson);
        serverDir().mkdir();
        home.join("server.yaml").writeString(serverYaml());
        versionFile().writeString(Main.versionString(world));
    }

    private String port() {
        return opts.getOrDefault("PORT_FIRST", "31000");
    }

    public String version() throws IOException {
        return versionFile().readString().trim();
    }

    public String serverYaml() throws IOException {
        String result;
        FileNode cisotools;
        int port;
        Map<String, String> map;

        result = world.resource("server.yaml").readString();
        cisotools = cisotools();
        port = Integer.parseInt(port());
        map = new HashMap<>();
        map.put("kubeConfig", kubeConfig());
        map.put("env", env(cisotools, port));
        map.put("mounts", mounts(cisotools));
        map.put("volumes", volumes(cisotools));
        try {
            return Substitution.ant().apply(result, map);
        } catch (SubstitutionException e) {
            throw new IllegalStateException(e);
        }
    }

    public String kubeConfig() throws IOException {
        return "apiVersion: v1\n"
                + "data:\n"
                + "  config: " + Base64.getEncoder().encodeToString(world.getHome().join(".kube/config").readBytes()) + "\n"
                + "kind: Secret\n"
                + "metadata:\n"
                + "  name: kube-config\n"
                + "  namespace: stool\n"
                + "type: Opaque\n";
    }

    public String env(FileNode cisotools, int port) throws IOException {
        Map<String, String> env;
        StringBuilder builder;
        String debugPort;
        FileNode file;

        env = new LinkedHashMap<>();
        debugPort = Integer.toString(port + 1);
        addIfNew(env, "HOST", LOCALHOST);
        addIfNew(env, "OPTS", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,quiet=n,address=*:" + debugPort);
        for (Map.Entry<String, String> entry : opts.entrySet()) {
            addIfNew(env, entry.getKey(), entry.getValue());
        }
        addIfNew(env, "ENGINE_LOG", "false"); // for engine wire logs
        addIfNew(env, "VHOSTS", Boolean.toString(hasDnsStar()));
        if (cisotools != null) {
            addIfNew(env, "LDAP_UNIT", "cisostages");
            addIfNew(env, "JMX_USAGE", "jconsole -J-Djava.class.path=$CISOTOOLS_HOME/stool/opendmk_jmxremote_optional_jar-1.0-b01-ea.jar service:jmx:jmxmp://localhost:%d");
            addIfNew(env, "ADMIN", "michael.hartmeier@ionos.com");
            if (todo != null) {
                addIfNew(env, "REGISTRY_URL", todo.getProperty("portus"));
            }
        }
        addIfNew(env, "LOGLEVEL", "INFO"); // for documentation purpose
        builder = new StringBuilder();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            builder.append("      - name: " + entry.getKey() + "\n");
            builder.append("        value: \"" + entry.getValue() + "\"\n");
        }
        return builder.toString();
    }

    public String mounts(FileNode cisotools) {
        StringBuilder builder;

        builder = new StringBuilder();
        addMount(builder, "kube-config", "/root/.kube", true);
        addMount(builder, "stool-server", "/var/lib/stool", false);
        if (cisotools != null) {
            addMount(builder, "fault-workspace", "/etc/fault/workspace", true);
        }
        return builder.toString();
    }

    public String volumes(FileNode cisotools) {
        StringBuilder builder;

        builder = new StringBuilder();
        addSecretVolume(builder, "kube-config");
        addVolume(builder, "stool-server", home.join("server").getAbsolute(), "Directory");
        if (cisotools != null) {
            addVolume(builder, "fault-workspace", world.getHome().join(".fault").getAbsolute(), "Directory");
        }
        return builder.toString();
    }

    private static void addMount(StringBuilder dest, String name, String path, boolean readOnly) {
         dest.append("      - name: " + name + "\n");
         dest.append("        mountPath: \"" + path + "\"\n");
         dest.append("        readOnly: " + readOnly + "\n");
    }

    private static void addVolume(StringBuilder dest, String name, String path, String type) {
        dest.append("    - name: " + name + "\n");
        dest.append("      hostPath:\n");
        dest.append("        path: \"" + path + "\"\n");
        dest.append("        type: " + type + "\n");
    }

    private static void addSecretVolume(StringBuilder dest, String name) {
        dest.append("    - name: " + name + "\n");
        dest.append("      secret:\n");
        dest.append("        secretName: \"" + name + "\"\n");
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
