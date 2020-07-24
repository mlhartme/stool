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
import net.oneandone.stool.docker.Daemon;
import net.oneandone.stool.docker.ImageInfo;
import net.oneandone.stool.util.Secrets;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.util.Substitution;
import net.oneandone.sushi.util.SubstitutionException;

import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Server {
    private static final String LOCALHOST = "localhost"; // TODO

    private final World world;
    private final FileNode dest;
    private final Console console;
    private final boolean overwrite;
    private final boolean resolve;
    private final String hostname;
    private final Map<String, String> opts;

    private final URI portus;

    public Server(Globals globals, boolean overwrite, boolean resolve, String hostname, List<String> args) throws IOException {
        int idx;
        String shortname;

        this.world = globals.getWorld();
        this.console = globals.getConsole();
        this.overwrite = overwrite;
        this.resolve = resolve;
        this.hostname = hostname;
        this.opts = new HashMap<>();
        shortname = eat(args, shortname(hostname));
        this.dest = world.file(eat(args, shortname + ".yaml"));
        for (String opt : args) {
            idx = opt.indexOf('=');
            if (idx == -1) {
                throw new ArgumentException("invalid option: " + opt);
            }
            this.opts.put(opt.substring(0, idx), opt.substring(idx + 1));
        }
        this.portus = Secrets.portus(world, getClass()).resolve(shortname + "/");
    }

    private static String eat(List<String> args, String dflt) {
        if (args.isEmpty()) {
            return dflt;
        } else {
            return args.remove(0);
        }
    }

    private static String shortname(String hostname) {
        String result;

        result = Strings.removeRightOpt(hostname, ".server.lan");
        result = result.replace('.', '-');
        return result;
    }

    private boolean isLocalhost() {
        return LOCALHOST.equals(hostname);
    }

    public void run() throws IOException {
        if (!overwrite) {
            dest.checkNotExists();
        }
        dest.writeString(serverYaml());
        console.info.println("done: " + dest);
    }

    public static void addIfNew(Map<String, String> env, String name, String value) {
        if (!env.containsKey(name)) {
            env.put(name, value);
        }
    }

    private int serverPort() {
        return 31000;
    }

    private static final Substitution DUBBLE = new Substitution("${{", "}}", '\\');

    public String serverYaml() throws IOException {
        String result;
        FileNode cisotools;
        int port;
        Map<String, String> map;

        if (isLocalhost()) {
            result = world.resource("local.yaml").readString();
            cisotools = Setup.cisotools(world);
            port = serverPort();
            map = new HashMap<>();
            map.put("env", env(cisotools, port));
            map.put("mounts", mounts(cisotools));
            map.put("volumes", volumes(cisotools));
            try {
                return Substitution.ant().apply(result, map);
            } catch (SubstitutionException e) {
                throw new IllegalStateException(e);
            }
        } else {
            map = new HashMap<>();
            map.put("portus", portus.toString());
            map.put("host", hostname);
            map.put("repositoryTag", repositoryTag());
            map.put("cert", tomcatP12(world, console, hostname));
            map.put("cert-script", certScript(world, hostname));
            result = world.resource("caas.yaml").readString();
            try {
                return Substitution.ant().apply(result, map);
            } catch (SubstitutionException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private String repositoryTag() throws IOException {
        if (resolve) {
            try (Daemon docker = Daemon.create()) {
                for (ImageInfo image : docker.imageList().values()) {
                    if (image.repositoryTags.remove("contargo.server.lan/cisoops-public/stool-server:latest")) {
                        if (image.repositoryTags.isEmpty()) {
                            throw new IllegalStateException();
                        }
                        if (image.repositoryTags.size() > 1) {
                            console.info.println("warning: repository tag ambiguous: " + image.repositoryTags);
                        }
                        return image.repositoryTags.get(0);
                    }
                }
            }
            throw new IOException("resolve failed");
        } else {
            return "latest";
        }
    }

    private static String certScript(World world, String hostname) throws IOException {
        String str;

        str = world.resource("caas-cert.sh").readString();
        try {
            str = DUBBLE.apply(str, Strings.toMap("domain", hostname));
        } catch (SubstitutionException e) {
            throw new IllegalStateException(e);
        }
        return Base64.getEncoder().encodeToString(str.getBytes("utf8"));
    }

    private static String tomcatP12(World world, Console console, String hostname) throws IOException {
        FileNode puppet;
        FileNode key;
        FileNode crt;
        FileNode ca;
        FileNode p12;
        FileNode chain;
        long p12modified;

        puppet = world.file(System.getenv("PUPPET_FILES_ROOT")).join("otherfiles/certificates").checkDirectory();
        p12 = puppet.join(hostname + ".p12");
        crt = puppet.join(hostname + ".crt");
        key = puppet.join(hostname + ".key");
        ca = puppet.join("ca/1und1PUKIIssuingCA2.pem");
        p12modified = p12.exists() ? p12.getLastModified() : Long.MIN_VALUE;
        if (crt.getLastModified() > p12modified || key.getLastModified() > p12modified || ca.getLastModified() > p12modified) {
            console.info.println("generating " + p12);
            chain = world.getTemp().createTempFile();
            chain.writeString(crt.readString() + ca.readString());
            puppet.exec("openssl", "pkcs12", "-export", "-in", chain.getAbsolute(),
                    "-inkey", key.getAbsolute(),
                    "-out", p12.getAbsolute(), "-name", "tomcat", "-passout", "pass:changeit");
        }
        return Base64.getEncoder().encodeToString(p12.readBytes());
    }

    public String env(FileNode cisotools, int port) {
        Map<String, String> env;
        String modules;
        StringBuilder builder;
        String debugPort;

        env = new LinkedHashMap<>();
        debugPort = Integer.toString(port + 1);
        addIfNew(env, "HOST", LOCALHOST);

        modules = "--add-exports=java.naming/com.sun.jndi.ldap=ALL-UNNAMED --illegal-access=deny";
        addIfNew(env, "OPTS", modules + " -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,quiet=n,address=*:" + debugPort);

        for (Map.Entry<String, String> entry : opts.entrySet()) {
            addIfNew(env, entry.getKey(), entry.getValue());
        }
        addIfNew(env, "ENGINE_LOG", "false"); // for engine wire logs
        if (cisotools != null) {
            addIfNew(env, "LDAP_UNIT", "cisostages");
            addIfNew(env, "JMX_USAGE", "jconsole -J-Djava.class.path=$CISOTOOLS_HOME/stool/opendmk_jmxremote_optional_jar-1.0-b01-ea.jar service:jmx:jmxmp://localhost:%d");
            addIfNew(env, "ADMIN", "michael.hartmeier@ionos.com");
            addIfNew(env, "REGISTRY_URL", portus.toString());
        }
        addIfNew(env, "LOGLEVEL", "INFO"); // for documentation purpose
        builder = new StringBuilder();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            builder.append("          - name: " + entry.getKey() + "\n");
            builder.append("            value: \"" + entry.getValue() + "\"\n");
        }
        return builder.toString();
    }

    public String mounts(FileNode cisotools) {
        StringBuilder builder;

        builder = new StringBuilder();
        if (cisotools != null) {
            addMount(builder, "fault-workspace", "/etc/fault/workspace", true);
        }
        return builder.toString();
    }

    public String volumes(FileNode cisotools) {
        StringBuilder builder;

        builder = new StringBuilder();
        if (cisotools != null) {
            addVolume(builder, "fault-workspace", world.getHome().join(".fault").getAbsolute(), "Directory");
        }
        return builder.toString();
    }

    private static void addMount(StringBuilder dest, String name, String path, boolean readOnly) {
         dest.append("          - name: " + name + "\n");
         dest.append("            mountPath: \"" + path + "\"\n");
         dest.append("            readOnly: " + readOnly + "\n");
    }

    private static void addVolume(StringBuilder dest, String name, String path, String type) {
        dest.append("        - name: " + name + "\n");
        dest.append("          hostPath:\n");
        dest.append("            path: \"" + path + "\"\n");
        dest.append("            type: " + type + "\n");
    }
}
