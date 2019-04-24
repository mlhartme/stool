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
import net.oneandone.inline.commands.PackageVersion;
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
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class Main {
    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    public static int run(String[] args) throws IOException {
        return run(world(), null, args);
    }

    public static int run(World world, FileNode itHome, String[] args) throws IOException {
        Cli cli;
        Globals globals;

        Console console;
        PrintWriter out;

        if (itHome != null) {
            out = new PrefixWriter(itHome.join("client.log").newAppender());
            console = new Console(out, out, System.in);
        } else {
            out = new PrefixWriter(new PrintWriter(System.out));
            console = new Console(out, out, System.in);
        }
        globals = new Globals(console, world, itHome == null ? world.getHome() : itHome,
                UUID.randomUUID().toString(), "stool " + Separator.SPACE.join(args));
        cli = new Cli(globals.console::handleException);
        loadDefaults(cli, world);
        cli.primitive(FileNode.class, "file name", world.getWorking(), world::file);
        cli.begin(globals.console, "-v=@verbose -e=@exception  { setVerbose(v) setStacktraces(e) }");
           cli.add(PackageVersion.class, "version");
           cli.begin("globals", globals,  "-wirelog -exception { setWirelog(wirelog) setException(exception) }");
              cli.addDefault(Help.class, "help command?");
              cli.begin("globals.world", "");
                       cli.begin("globals.console", "");
                cli.add(Auth.class, "auth server");
                cli.base(ClientCommand.class, "");
                    cli.add(Create.class, "create -project serverAndServer properties*");
                    cli.add(Build.class, "build -project -nocache -keep=5 -restart -m= warsAndArgs*");
                    cli.add(Attach.class, "attach -project stage");
                    cli.add(Detach.class, "detach -project");
                    cli.base(StageCommand.class, "-stage -all -fail { setStage(stage) setAll(all) setFail(fail) }");
                      cli.add(App.class, "app name*");
                      cli.add(Config.class, "config property* { property*(property) }");
                      cli.add(History.class, "history -details=@history.details:false -max=@history.max:-1");
                      cli.add(Ls.class, "list -defaults=@list.defaults info* { select*(info) }");
                      cli.add(Remove.class, "remove -batch -stop");
                      cli.add(Restart.class, "restart appIndex*");
                      cli.add(Start.class, "start -tail -http=-1 -https=-1 envAppIndex*");
                      cli.add(Status.class, "status -defaults=@status.defaults info* { select*(info) }");
                      cli.add(Stop.class, "stop app*");
                      cli.add(Validate.class, "validate -email -repair");

        return cli.run(args);
    }

    private static void loadDefaults(Cli cli, World world) throws IOException {
        FileNode file;
        Properties p;

        file = world.getHome().join(".stool.defaults");
        if (file.exists()) {
            p = file.readProperties();
            cli.defaults((Map) p);
        }
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
                protocol.equals("https") ? (LAZY_HOSTS.contains(hostname) ? lazyFactory() : SSLSocketFactory.getDefault())  : null );
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
}
