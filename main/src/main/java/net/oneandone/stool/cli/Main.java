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
package net.oneandone.stool.cli;

import net.oneandone.inline.Cli;
import net.oneandone.inline.Console;
import net.oneandone.inline.commands.PackageVersion;
import net.oneandone.stool.setup.Lib;
import net.oneandone.stool.util.LogOutputStream;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.ReadLinkException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.InputLogStream;
import net.oneandone.sushi.io.MultiOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Properties;

public class Main {
    public static void main(String[] args) throws IOException {
        System.exit(doRun(args));
    }

    public static int doRun(String[] args) throws IOException {
        World world;
        FileNode lib;
        String user;

        world = World.create();
        lib = locateLib(world);
        user = System.getProperty("user.name");
        return doRun(user, null, lib, args);
    }

    public static int doRun(String user, Logging logging, FileNode lib, String[] args) throws IOException {
        World world;
        Cli cli;
        String command;
        boolean setenv;
        Globals globals;
        Console console;

        world = lib.getWorld();
        if (!lib.exists()) {
            Lib.create(Console.create(), lib, null);
        }
        if (logging == null) {
            setenv = true;
            // i need lib with a proper logs directory first ...
            logging = Logging.forLib(lib, user);
            console = console(logging, System.out, System.err);
        } else {
            // for integration dests
            setenv = false;
            OutputStream devNull = MultiOutputStream.createNullStream();
            console = console(logging, devNull, devNull);
        }
        command = "stool " + command(args);
        logging.log("COMMAND", command);
        globals = new Globals(setenv, lib, logging, user, command, console, world);
        cli = new Cli(globals::handleException);
        loadDefaults(cli, world);
        cli.primitive(FileNode.class, "file name", world.getWorking(), world::file);
        cli.begin(console, "-v=@verbose -e=@exception  { setVerbose(v) setStacktraces(e) }");
           cli.add(PackageVersion.class, "version");
           cli.begin("globals", globals,  "-svnuser=null -svnpassword=null -exception { setSvnuser(svnuser) setSvnpassword(svnpassword) setException(exception) }");
              cli.begin("globals.session", "");
                cli.addDefault(Help.class, "help command?=null");
                cli.base(SessionCommand.class, "-nolock { setNoLock(nolock) }");
                    cli.add(Create.class, "create -quiet -name=null url directory?=null property* { property*(property) }");
                    cli.add(Import.class, "import -name=@import.name:%d -max=@import.max:40 dir* { dirs*(dir) setMax(max) setName(name) }");
                    cli.add(Select.class, "select -fuzzy=@select.fuzzy name?=null");
                    cli.base(StageCommand.class, "-autorechown=@auto.rechown -autochown=@auto.chown -autorestart=@auto.restart -autostop=@auto.stop -stage=null -all -fail "
                            + "{ setAutoRechown(autorechown) setAutoChown(autochown) setAutoRestart(autorestart) setAutoStop(autostop) "
                            +   "setStage(stage) setAll(all) setFail(fail) }");
                      cli.add(Build.class, "build");
                      cli.add(Cd.class, "cd target?=null { setTarget(target) }");
                      cli.add(Chown.class, "chown -batch user?=null");
                      cli.add(Cleanup.class, "cleanup");
                      cli.add(Config.class, "config property* { property*(property) }");
                      cli.add(History.class, "history -max=@history.max:999 detail* { detail*(detail) }");
                      cli.add(Ls.class, "list -defaults=@list.defaults field* { field*(field) }");
                      cli.add(Move.class, "move dest");
                      cli.add(Port.class, "port port { port*(port) }");
                      cli.add(Refresh.class, "refresh -build=@refresh.build -restore");
                      cli.add(Remove.class, "remove -batch -force -backstage { setBackstage(backstage) }");
                      cli.add(Restart.class, "restart -debug=@tomcat.debug -suspend=@tomcat.suspend");
                      cli.add(Start.class, "start -debug=@tomcat.debug -suspend=@tomcat.suspend -tail { setTail(tail) }");
                      cli.add(Status.class, "status -defaults=@status.defaults field* { field*(field) }");
                      cli.add(Stop.class, "stop -sleep");
                      cli.add(SystemStart.class, "system-start");
                      cli.add(SystemStop.class, "system-stop");
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

    public static class SystemStart extends SystemStartStop {
        public SystemStart(Session session) {
            super(session, true);
        }
    }
    public static class SystemStop extends SystemStartStop {
        public SystemStop(Session session) {
            super(session, false);
        }

    }
    public static Console console(Logging logging, OutputStream out, OutputStream err) {
        return new Console(logging.writer(out, "OUT"), logging.writer(err, "ERR"),
                new InputLogStream(System.in, new LogOutputStream(logging, "IN")));
    }

    /** hide shell and svn arguments */
    private static String command(String[] args) {
        StringBuilder result;
        boolean options;
        String arg;

        result = new StringBuilder();
        options = true;
        for (int i = 0; i < args.length; i++) {
            arg = args[i];
            if (options) {
                switch (arg) {
                    case "-shell":
                        i++;
                        continue;
                    case "-svnuser":
                    case "-svnpassword":
                        arg = arg + " ********";
                        i++;
                        break;
                    default:
                        if (!arg.startsWith("-")) {
                            options = false;
                        }
                }
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(arg);
        }
        return result.toString();
    }

    //--

    public static FileNode locateLib(World world) throws ReadLinkException {
        FileNode cp;

        cp = stoolCp(world);
        if (cp.getParent().getPath().equals("usr/bin")) {
            return world.file("usr/share/stool");
        } else {
            return world.getHome().join(".stool");
        }
    }

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

    public static FileNode stoolCp(World world) {
        return world.locateClasspathItem(world.getClass());
    }
}
