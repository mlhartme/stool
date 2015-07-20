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

import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.Slf4jOutputStream;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Child;
import net.oneandone.sushi.cli.Cli;
import net.oneandone.sushi.cli.Command;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Parser;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.InputLogStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class Main extends Cli implements Command {
    public static void main(String[] args) throws IOException {
        System.exit(doRun(args));
    }

    public static int doRun(String[] args) throws IOException {
        String user;
        World world;
        Environment environment;
        FileNode home;
        Logging logging;
        String command;
        Console console;
        Main main;

        world = new World();
        user = System.getProperty("user.name");
        environment = Environment.loadSystem();
        home = environment.stoolHome(world);
        home.checkDirectory();
        logging = Logging.forStool(home, user);
        command = "stool " + command(args);
        logging.logger("COMMAND").info(command);
        console = console(world, logging, System.out, System.err);
        main = new Main(logging, user, command, environment, console);

        try {
            return main.run(args);
        } catch (ArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            // this is a bug
            main.session().reportException("RuntimeException", e);
            throw e;
        }
    }

    public static Console console(World world, Logging logging, OutputStream out, OutputStream err) throws IOException {
        return new Console(world, logging.writer(out, "OUT"), logging.writer(err, "ERR"),
                new InputLogStream(System.in, new Slf4jOutputStream(logging.logger("IN"), true)));
    }

    /** hide invocation and svn arguments */
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
                    case "-invocation":
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

    public static final String INBOX = "inbox";

    private final Logging logging;
    private final String user;
    private final String command;
    private final Environment environment;
    private Session session;

    @Option("svnuser")
    private String svnuser;

    @Option("svnpassword")
    private String svnpassword;

    @Option("exception")
    private boolean exception;

    @Option("invocation")
    private FileNode invocationFile;

    public Main(Logging logging, String user, String command, Environment environment, Console console) {
        super(console);
        this.logging = logging;
        this.user = user;
        this.command = command;
        this.environment = environment;
        this.session = null;
    }

    // TODO: same as sushi's run method, but does not catch RuntimeExceptions
    @Override
    public int run(String... args) {
        Parser parser;
        Command command;

        parser = Parser.create(schema, getClass());
        try {
            command = (Command) parser.run(this, args);
            console.verbose.println("command line: " + Arrays.asList(args));
            if (pretend) {
                console.info.println("pretend-only, command " + command + " is not executed");
            } else {
                command.invoke();
            }
        } catch (ArgumentException e) {
            console.error.println(e.getMessage());
            console.info.println("Specify 'help' to get a usage message.");
            e.printStackTrace(exception ? console.error : console.verbose);
            return -1;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            console.error.println(e.getMessage());
            e.printStackTrace(exception ? console.error : console.verbose);
            return -1;
        }
        return 0;
    }

    @Child("build")
    public Build build() throws IOException {
        return new Build(session());
    }

    @Child("start")
    public Start start() throws IOException {
        return new Start(session(), false, false);
    }

    @Child("stop")
    public Stop stop() throws IOException {
        return new Stop(session());
    }

    @Child("remove")
    public Remove remove() throws IOException {
        return new Remove(session());
    }

    @Child("move")
    public Move move() throws IOException {
        return new Move(session());
    }

    @Child("rename")
    public Rename rename() throws IOException {
        return new Rename(session());
    }

    @Child("restart")
    public Restart restart() throws IOException {
        return new Restart(session());
    }

    @Child("create")
    public Create create() throws IOException {
        return new Create(session());
    }

    @Child("import")
    public Import imprt() throws IOException {
        return new Import(session());
    }

    @Child("select")
    public Select select() throws IOException {
        return new Select(session());
    }

    @Child("chown")
    public Chown chown() throws IOException {
        return new Chown(session());
    }

    @Child("refresh")
    public Refresh refresh() throws IOException {
        return new Refresh(session());
    }

    @Child("status")
    public Status status() throws IOException {
        return new Status(session());
    }

    @Child("config")
    public Config config() throws IOException {
        return new Config(session());
    }

    @Child("cleanup")
    public Cleanup cleanup() throws IOException {
        return new Cleanup(session());
    }

    @Child("list")
    public Ls list() throws IOException {
        return new Ls(session());
    }

    @Child("validate")
    public Validate validate() throws IOException {
        return new Validate(session());
    }

    @Child("history")
    public History history() throws IOException {
        return new History(session());
    }

    @Child("cd")
    public Cd cd() throws IOException {
        return new Cd(session());
    }

    @Child("system-start")
    public SystemStartStop systemStart() throws IOException {
        return new SystemStartStop(session(), true);
    }

    @Child("system-stop")
    public SystemStartStop systemStop() throws IOException {
        return new SystemStartStop(session(), false);
    }

    @Child("system-validate")
    public SystemValidate systemValidate() throws IOException {
        return new SystemValidate(session());
    }

    @Child("system-import")
    public SystemImport systemImport() throws IOException {
        return new SystemImport(session());
    }

    @Override
    public void printHelp() {
        String stoolhome;

        if (exception) {
            throw new RuntimeException("intentional exception");
        }
        stoolhome = environment.stoolHome(console.world).getAbsolute();
        console.info.println("Stool usage:");
        console.info.println("  stool <global-options> <command>");
        console.info.println();
        console.info.println("general commands:");
        console.info.println("  help");
        console.info.println("  version");
        console.info.println("  create");
        console.info.println("  import");
        console.info.println("  select");
        console.info.println("stage commands");
        console.info.println("  config");
        console.info.println("  status");
        console.info.println("  build");
        console.info.println("  refresh");
        console.info.println("  start");
        console.info.println("  stop");
        console.info.println("  restart");
        console.info.println("  move");
        console.info.println("  rename");
        console.info.println("  remove");
        console.info.println("  validate");
        console.info.println("  history");
        console.info.println("  chown");
        console.info.println("  cd");
        console.info.println("  cleanup");
        console.info.println("  list");
        console.info.println("system commands:");
        console.info.println("  system-stop");
        console.info.println("  system-start");
        console.info.println("  system-validate");
        console.info.println("  system-import");
        console.info.println();
        console.info.println("select options for stage commands");
        console.info.println("  (no select option)      operate on the selected stage");
        console.info.println("  -all                    operate on all stages");
        console.info.println("  -stage <predicate>      operate on all stages matching the specified predicate");
        console.info.println();
        console.info.println("failure mode for stage commands");
        console.info.println("  -fail normal       fail immediately");
        console.info.println("  -fail after        fail after all stages have been tried");
        console.info.println("  -fail never        report errors as warning");
        console.info.println();
        console.info.println("global options");
        console.info.println("  -v  verbose output");
        console.info.println("  -e  print stacktrace for all errors");
        console.info.println("  -svnuser      user name for svn");
        console.info.println("  -svnpassword  password for svn");
        console.info.println();
        console.info.println("home directory: " + stoolhome);
        console.info.println();
        console.info.println("selection predicates");
        console.info.println("  or        = and {',' and}");
        console.info.println("  and       = cmp {'+' cmp}");
        console.info.println("  cmp       = (FIELD | PROPERTY) ('=' | '!=') (VALUE | prefix | suffix | substring) ");
        console.info.println("  prefix    = VALUE '*'");
        console.info.println("  suffix    = '*' VALUE");
        console.info.println("  substring = '*' VALUE '*'");
        console.info.println("  FIELD     # status field");
        console.info.println("  PROPERTY  # configuration property");
        console.info.println();
        console.info.println("example urls: ");
        console.info.println("  gav:net.oneandone:hellowar:1.0.2  (artifact stage)");
        console.info.println("  https://github.com/mlhartme/hellowar/trunk (source stage)");
        console.info.println();
        console.info.println("documentation:");
        console.info.println("  Homepage: https://github.com/mlhartme/stool");
        console.info.println("  Invoke 'man stool-<command>' to get documentation for individual commands.");
    }

    private Session session() throws IOException {
        if (session == null) {
            session = Session.load(logging, user, command, environment, console, invocationFile, svnuser, svnpassword);
        }
        return session;
    }

    @Override
    public void invoke() throws Exception {
        printHelp();
    }
}
