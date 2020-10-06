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

import net.oneandone.inline.Cli;
import net.oneandone.inline.Console;
import net.oneandone.stool.client.Globals;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.Proxy;
import net.oneandone.sushi.io.PrefixWriter;
import net.oneandone.sushi.util.Separator;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    public static int run(String[] args) throws IOException {
        return run(world(), null, args);
    }

    public static int run(World world, FileNode clientYaml, String[] args) throws IOException {
        Cli cli;
        Globals globals;

        Console console;
        PrintWriter out;

        if (clientYaml != null) {
            out = new PrefixWriter(clientYaml.getParent().join("client.log").newAppender());
            console = new Console(out, out, System.in);
        } else {
            out = new PrefixWriter(new PrintWriter(System.out));
            console = new Console(out, out, System.in);
        }
        globals = Globals.create(console, world, clientYaml, "stool " + Separator.SPACE.join(args));
        cli = new Cli(globals.getConsole()::handleException);
        cli.primitive(FileNode.class, "file name", null, world::file);
        cli.begin(globals.getConsole(), "-v -e  { setVerbose(v) setStacktraces(e) }");
           cli.begin("globals", globals,  "-context -wirelog -exception { setContext(context) setWirelog(wirelog) setException(exception) }");
              cli.add(Version.class, "version");
              cli.addDefault(Help.class, "help command?");
              cli.begin("globals.getWorld", ""); cli.begin("globals.getConsole", "");
                 cli.add(Setup.class, "setup nameAndHost?");
                 cli.add(Auth.class, "auth -batch");
                 cli.add(ConfigContext.class, "context -offline -q name?");
                 cli.add(ShellInc.class, "shell-inc");
                 cli.add(Server.class, "server -overwrite -resolve fqdn api args*");
                 cli.add(Create.class, "create -optional -detached args+"); // args: path* name (key=value)*
                 cli.add(Attach.class, "attach args+"); // args: path* name
                 cli.base(StageCommand.class, "-stage -all -fail { setStage(stage) setAll(all) setFail(fail) }");
                    cli.add(Detach.class, "detach");
                    cli.add(Build.class, "build -app -nocache -keep=3 -restart -m= args*");
                    cli.add(Config.class, "config property* { property*(property) }");
                    cli.add(History.class, "history -details=false -max=-1");
                    cli.add(Images.class, "images");
                    cli.add(Ls.class, "list info* { select*(info) }");
                    cli.add(Delete.class, "delete -batch -stop");
                    cli.add(Remove.class, "remove");
                    cli.add(Restart.class, "restart image?");
                    cli.add(Start.class, "start -http=-1 -https=-1 envAppIndex*");
                    cli.add(Status.class, "status info* { select*(info) }");
                    cli.add(Stop.class, "stop");
                    cli.add(PortForward.class, "port-forward -timeout=30 port toPort?");
                    cli.add(Ssh.class, "ssh -timeout=30 shell?");
                    cli.add(Validate.class, "validate -email -repair");

        return cli.run(args);
    }

    //--

    private static final List<String> LAZY_HOSTS = Arrays.asList("api-next.pki.1and1.org");

    public static World world() throws IOException {
        World world;

        world = World.create();
        update(world, "http");
        update(world, "https");
        if (System.getProperty("stool.wire") != null) {
            HttpFilesystem.wireLog("/tmp/stool.wire");
        }
        return world;
    }

    private static void update(World world, String scheme) {
        HttpFilesystem fs;
        Proxy proxy;

        fs = (HttpFilesystem) world.getFilesystem(scheme);
        fs.setSocketFactorySelector((protocol, hostname) ->
                protocol.equals("https") ? (LAZY_HOSTS.contains(hostname) ? lazyFactory() : SSLSocketFactory.getDefault()) : null);
        if (fs.getProxy(scheme) == null) {
            proxy = Proxy.forPropertiesOpt("stool." + scheme);
            if (proxy == null) {
                proxy = Proxy.forPropertiesOpt(scheme);
                if (proxy == null) {
                    proxy = Proxy.forEnvOpt(scheme);
                }
            }
            if (proxy != null) {
                fs.setProxy(scheme, proxy);
            }
        }
    }

    public static SSLSocketFactory lazyFactory() {
        TrustManager[] trustAllCerts;
        SSLContext sc;

        trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };
        try {
            sc = SSLContext.getInstance("SSL");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
        try {
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (KeyManagementException e) {
            throw new IllegalArgumentException(e);
        }
        return sc.getSocketFactory();
    }


    public static String versionString(World world) {
        // don't use class.getPackage().getSpecificationVersion() because META-INF/META.MF is not available in test cases
        try {
            return world.resource("stool-client.version").readString().trim();
        } catch (IOException e) {
            throw new IllegalStateException("cannot determine version", e);
        }
    }

    private Main() {
    }
}
