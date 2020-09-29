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
import java.util.List;
import java.util.Map;

public class Server {
    private final World world;
    private final FileNode dest;
    private final Console console;
    private final boolean overwrite;
    private final boolean resolve;
    private final String hostname;
    private final String api;

    private final Secrets secrets;
    private final URI portusWithShortName;

    public Server(Globals globals, boolean overwrite, boolean resolve, String hostname, String api, List<String> args) throws IOException {
        String shortname;

        this.world = globals.getWorld();
        this.console = globals.getConsole();
        this.overwrite = overwrite;
        this.resolve = resolve;
        this.hostname = hostname;
        this.api = api;
        shortname = eat(args, shortname(hostname));
        this.dest = world.file(eat(args, shortname + ".yaml"));
        if (!args.isEmpty()) {
            throw new ArgumentException("unknown arguments: " + args);
        }
        this.secrets = Secrets.load(world);
        this.portusWithShortName = secrets.portus.resolve(shortname + "/");
    }

    private static String eat(List<String> args, String dflt) {
        if (args.isEmpty() || args.get(0).contains("=")) {
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
        return "localhost".equals(hostname);
    }

    public void run() throws IOException {
        if (!overwrite) {
            dest.checkNotExists();
        }
        dest.writeString(serverYaml());
        console.info.println("done: " + dest);
    }

    private static final Substitution DUBBLE = new Substitution("${{", "}}", '\\');

    public String serverYaml() throws IOException {
        String result;
        Map<String, String> map;

        map = new HashMap<>();
        map.put("portus", portusWithShortName.toString());
        map.put("api", api);
        map.put("ldapUnit", secrets.ldapUnit);
        map.put("ldapUrl", secrets.ldapUrl);
        map.put("ldapPrincipal", secrets.ldapPrincipal);
        map.put("ldapCredentials", secrets.ldapCredentials);
        map.put("ldapSso", secrets.ldapSso);
        map.put("host", hostname);
        if (isLocalhost()) {
            map.put("home", world.getHome().getAbsolute());
            result = world.resource("local.yaml").readString();
            try {
                return Substitution.ant().apply(result, map);
            } catch (SubstitutionException e) {
                throw new IllegalStateException(e);
            }
        } else {
            map.put("hostkey", hostkey(world, shortname(hostname) + ".key"));
            map.put("hostkey-pub", hostkey(world, shortname(hostname) + ".key.pub"));
            map.put("faultName", "public_" + shortname(hostname).replace('-', '_'));
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

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String certScript(World world, String hostname) throws IOException {
        String str;

        str = world.resource("caas-cert.sh").readString();
        try {
            str = DUBBLE.apply(str, Strings.toMap("domain", hostname));
        } catch (SubstitutionException e) {
            throw new IllegalStateException(e);
        }
        return base64(str.getBytes("utf8"));
    }

    // TODO: cannot take tomcat.p12 from fault project because it does not support binary files
    private static String tomcatP12(World world, Console console, String hostname) throws IOException {
        FileNode base;
        FileNode key;
        FileNode crt;
        FileNode ca;
        FileNode p12;
        FileNode chain;
        long p12modified;

        base = Secrets.secrets(world);
        p12 = base.join(hostname + ".p12");
        crt = base.join(hostname + ".crt");
        key = base.join(hostname + ".key");
        ca = base.join("pukiIssuingCA2.pem");
        p12modified = p12.exists() ? p12.getLastModified() : Long.MIN_VALUE;
        if (crt.getLastModified() > p12modified || key.getLastModified() > p12modified || ca.getLastModified() > p12modified) {
            console.info.println("generating " + p12);
            chain = world.getTemp().createTempFile();
            chain.writeString(crt.readString() + ca.readString());
            base.exec("openssl", "pkcs12", "-export", "-in", chain.getAbsolute(),
                    "-inkey", key.getAbsolute(),
                    "-out", p12.getAbsolute(), "-name", "tomcat", "-passout", "pass:changeit");
        }
        return base64(p12.readBytes());
    }

    private static String hostkey(World world, String name) throws IOException {
        return base64(Secrets.secrets(world).join(name).readBytes());
    }
}
