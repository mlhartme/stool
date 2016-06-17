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
package net.oneandone.stool;

import net.oneandone.inline.Cli;
import net.oneandone.inline.Console;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.Slf4jOutputStream;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.InputLogStream;

import java.io.IOException;
import java.io.OutputStream;

public class Main {
    public static void main(String[] args) throws IOException {
        System.exit(doRun(args));
    }

    public static int doRun(String[] args) throws IOException {
        String user;
        World world;
        Environment environment;
        Cli cli;
        FileNode lib;
        Logging logging;
        String command;
        Console console;

        world = World.create();
        user = System.getProperty("user.name");
        environment = Environment.loadSystem();
        lib = Session.locateLib(environment.stoolBin(world));
        lib.checkDirectory();
        logging = Logging.forStool(lib, user);
        command = "stool " + command(args);
        logging.logger("COMMAND").info(command);
        console = console(logging, System.out, System.err);

        cli = new Cli();
        cli.primitive(FileNode.class, "file name", world.getWorking(), world::file);
        // TODO: cli.begin(console, "-v -e  { setVerbose(v) setStacktraces(e) }");
        cli.begin("globals", new Globals(logging, user, command, environment, console, world),
                "-nolock -svnuser -svnpassword -exception -shell " +
                        "{ setNoLock(nolock) setSvnuser(svnuser) setSvnpassword(svnpassword) setException(exception) setShell(shell) ");
          cli.begin("globals.session", "");
            cli.base(SessionCommand.class, "-nolock { setNoLock(nolock) }");
                cli.add(Help.class, "command?=null");
                cli.add(Create.class, "-quiet -name url directory property* { property*(property) }");
                cli.add(Import.class, "-name=null -max=40 dir* { dirs*(dir) setMax(max) setName(name) }");
                cli.add(Select.class, "name?=null");
                cli.base(StageCommand.class, "-autorechown -autochown -autorestart -autostop -stage=null -all -fail "
                        + "{ setAutoRechown(autorechown) setAutoChown(autochown) setAutoRestart(autorestart) setAutoStop(autostop) "
                        +   "setStage(stage) setAll(all) setFail(fail) }");
                  cli.add(Build.class, "");
                  cli.add(Cd.class, "target?=null { setTarget(target) }");
                  cli.add(Chown.class, "-batch user?=null");
                  cli.add(Cleanup.class, "");
                  cli.add(Config.class, "property* { property*(property) }");
                  cli.add(History.class, "-max=999 detail* { detail*(detail) }");
                  cli.add(Ls.class, "");
                  cli.add(Move.class, "dest");
                  cli.add(Port.class, "port { port*(port) }");
                  cli.add(Refresh.class, "-build -restore");
                  cli.add(Remove.class, "-batch -force -backstage { setBackstage(backstage) }");
                  cli.add(Rename.class, "name");
                  cli.add(Restart.class, "-debug -suspend { setDebug(debug) setSuspend(suspend) }");
                  cli.add(Start.class, "-debug -suspend -tail { setTail(tail) }");
                  cli.add(Status.class, "field* { field*(field) }");
                  cli.add(Stop.class, "-sleep");
                  cli.add(Validate.class, "-email -repair");

        return cli.run(args);
    }

    public static Console console(Logging logging, OutputStream out, OutputStream err) {
        return new Console(logging.writer(out, "OUT"), logging.writer(err, "ERR"),
                new InputLogStream(System.in, new Slf4jOutputStream(logging.logger("IN"), true)));
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
}
