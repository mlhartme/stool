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
package net.oneandone.stool.cli;

import net.oneandone.inline.Cli;
import net.oneandone.inline.Console;
import net.oneandone.inline.commands.PackageVersion;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.LogOutputStream;
import net.oneandone.stool.util.Logging;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.Proxy;
import net.oneandone.sushi.io.InputLogStream;
import net.oneandone.sushi.io.MultiOutputStream;
import net.oneandone.sushi.util.Separator;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Main {
    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    public static int run(String[] args) throws IOException {
        return run(Environment.loadSystem(), world(), false, args);
    }

    public static int run(Environment environment, World world, boolean it, String[] args) throws IOException {
        Logging logging;
        FileNode home;
        Cli cli;
        String command;
        Globals globals;
        Console console;
        FileNode tmp;

        home = environment.locateHome(world);
        if (home.exists()) {
            logging = Logging.forHome(home, environment.detectUser());
        } else {
            tmp = world.getTemp().createTempDirectory();
            logging = new Logging("1", tmp.join("homeless"), environment.detectUser());
        }
        if (it) {
            OutputStream devNull = MultiOutputStream.createNullStream();
            console = console(logging, devNull, devNull);
        } else {
            console = console(logging, System.out, System.err);
        }
        command = "stool " + Separator.SPACE.join(args);
        logging.command(command);
        globals = new Globals(environment, home, logging, command, console, world);
        cli = new Cli(globals::handleException);
        loadDefaults(cli, world);
        cli.primitive(FileNode.class, "file name", world.getWorking(), world::file);
        cli.begin(console, "-v=@verbose -e=@exception  { setVerbose(v) setStacktraces(e) }");
           cli.add(PackageVersion.class, "version");
           cli.begin("globals", globals,  "-exception { setException(exception) }");
              cli.addDefault(Help.class, "help command?");
              cli.add(Setup.class, "setup -batch config? { config(config) }");
              cli.begin("globals.session", "");
                cli.base(SessionCommand.class, "-nolock { setNoLock(nolock) }");
                    cli.add(Create.class, "create projectAndProperties*");
                    cli.add(Build.class, "build -nocache -keep=5 -restart -m= project?");
                    cli.add(Attach.class, "attach stage project?");
                    cli.add(Detach.class, "detach project?");
                    cli.add(Port.class, "port port project? { port*(port) }");
                    cli.base(StageCommand.class, "-stage -all -fail { setStage(stage) setAll(all) setFail(fail) }");
                      cli.add(Images.class, "images");
                      cli.add(Cleanup.class, "cleanup");
                      cli.add(Config.class, "config property* { property*(property) }");
                      cli.add(History.class, "history -details=@history.details:false -max=@history.max:50");
                      cli.add(Ls.class, "list -defaults=@list.defaults info* { select*(info) }");
                      cli.add(Remove.class, "remove -batch -stop");
                      cli.add(Restart.class, "restart appIndex*");
                      cli.add(Start.class, "start -tail appIndex*");
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

    public static Console console(Logging logging, OutputStream out, OutputStream err) {
        return new Console(logging.writer(out, "OUT"), logging.writer(err, "ERR"),
                new InputLogStream(System.in, new LogOutputStream(logging, "IN")));
    }

    //--

    public static String versionString(World world) {
        // don't use class.getPackage().getSpecificationVersion() because META-INF/META.MF
        // 1) is not available in Webapps (in particular: dashboard)
        // 2) is not available in test cases
        try {
            return world.resource("stool.version").readString().trim();
        } catch (IOException e) {
            throw new IllegalStateException("cannot determine version", e);
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
