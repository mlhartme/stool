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

import ch.qos.logback.classic.Level;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.ErrorTool;
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
import net.oneandone.sushi.util.Separator;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
        Logger inputLogger;
        InputStream input;
        Console console;
        Main main;

        world = new World();
        user = System.getProperty("user.name");
        environment = Environment.loadSystem();
        home = environment.stoolHome(world);
        home.checkDirectory();
        logging = Logging.forHome(home, user);
        command = "stool " + command(args);
        inputLogger = logging.logger("IN");
        input = new InputLogStream(System.in, new Slf4jOutputStream(inputLogger, true));
        inputLogger.info(command);
        console = new Console(world, logging.writer(System.out, "OUT"), logging.writer(System.err, "ERR"), input);
        main = new Main(logging, user, command, environment, console);

        try {
            return main.run(args);
        } catch (RuntimeException e) {
            try {
                Session session;
                StoolConfiguration config;

                session = main.session();
                config = session.configuration;
                if (config.errorTool != null) {
                    ErrorTool.send(new URL(config.errorTool), Level.ERROR, session.user + "@" + config.hostname, session.command, e);
                }
            } catch (Exception nested) {
                e.addSuppressed(nested);
            }
            throw e;
        }
    }

    private static String command(String[] args) {
        if (args.length >= 2 && args[0].equals("-invocation")) {
            args = Arrays.copyOfRange(args, 2 /* skip invocation file */, args.length);
        }
        return Separator.SPACE.join(args);
    }

    public static final String INBOX = "inbox";

    private final Logging logging;
    private final String user;
    private final String command;
    private final Environment environment;
    private Session session;

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
    public SystemStart systemStart() throws IOException {
        return new SystemStart(session());
    }


    @Child("system-stop")
    public SystemStop systemStop() throws IOException {
        return new SystemStop(session());
    }

    @Child("system-validate")
    public SystemValidate systemValidate() throws IOException {
        return new SystemValidate(session());
    }

    @Child("system-import")
    public SystemImport systemImport() throws IOException {
        return new SystemImport(session());
    }

    //--
    @Child("duso")
    public Sudo sudo() throws IOException {
        return new Sudo(session());
    }

    @Override
    public void printHelp() {
        String stoolhome;

        stoolhome = environment.stoolHome(console.world).getAbsolute();
        console.info.println("Stool usage:");
        console.info.println("  stool <global-options> <command>");
        console.info.println();
        console.info.println("general commands:");
        console.info.println("  help            print this message");
        console.info.println("  version         print stool version information");
        console.info.println("  create [-quiet|-prefix <n>] <url> [<name>] [<directory>] {<key>=<val>}");
        console.info.println("  .....................................  creates a new stage named <name> from <url>");
        console.info.println("  import [-max <n>] [-name <template>] <directory>* ......");
        console.info.println("                                         turns existing applications into a stage; template is a string");
        console.info.println("                                         if it contains %d, it will be replaced by the directory name");
        console.info.println("                                         it defaults to just %d");
        console.info.println("  select [<stage>|none]  ..............  use <stage> for stage commands");
        console.info.println("stage commands");
        console.info.println("  config {<key>[=<val>]}  .............  get/set stage properties;");
        console.info.println("  .....................................  invoke without arguments to get a list of available keys");
        console.info.println("  status {<field>} ....................  prints selected status fields (default: all)");
        console.info.println("  build  ..............................  builds the stage; for convenience only -- you're free to build ");
        console.info.println(
          "  .....................................  your application as you normally do, stool configure the necessary");
        console.info.println("  .....................................  environment variables");
        console.info.println("  refresh [-build|-stop|-own|-debug] ..  update the stage, with optional building;");
        console.info.println("  .....................................  restart and chowning when necessary");
        console.info.println("  start [-debug|-suspend|-tail] .......  starts the stage, and optionally tails catalina.out");
        console.info.println("  stop [-sleep] .......................  stops the stage");
        console.info.println("  restart [-debug] ....................  restarts the stage");
        console.info.println("  move <dest> .........................  changes the stage directory; the stage name is not changed");
        console.info.println("  rename <name> .......................  changes the stage name; the stage directory is not changed");
        console.info.println("  remove [-force|-batch|-wrapper] .....  removes the stage after modified file check and confirm");
        console.info.println(
          "  validate [-email|-stop] .............  validates the stage, optionally sends emails and stops + deletes stages.");
        console.info.println("  history [-max <n>] <n> ..............  shows the command history for a stage");
        console.info.println("  chown [-stop] [<user>] ..............  changes the stage owner by changing file ownership");
        console.info.println("  .....................................  (user defaults to current user)");
        console.info.println("  cd [<target>] .......................  changes to specified directory, e.g. wrapper, tomcat or empty");
        console.info.println("  cleanup .............................  removes the maven repository and rotates *.log into an archive");
        console.info.println(
          "  list  ...............................  prints a short status of the selected stages; default selection is -all");
        console.info.println("admin commands:");
        console.info.println("  system-stop [-sleep] ................  stops the overviews and sends stages to sleep.");
        console.info.println("  system-start [-awake] ...............  starts the overview and awakes stages");
        console.info.println("  system-validate ..... ...............  checks for inconsistencies and new stool versions");
        console.info.println("  system-import [-include all|config|stages] <old-home> {name}");
        console.info.println("                                         imports global config and the specified stages (default: all)");
        console.info.println();
        console.info.println("select options for stage commands");
        console.info.println("  (no select option)      operate on the selected stage");
        console.info.println("  -all                    operate on all stages");
        console.info.println("  -stage <predicate>      operate on all stages matching the specified predicate");
        console.info.println();
        console.info.println("failure mode for stage command");
        console.info.println("  -fail normal       fail immediately");
        console.info.println("  -fail after        fail after all stages have been tried");
        console.info.println("  -fail never        report errors as warning");
        console.info.println();
        console.info.println("global options");
        console.info.println("  -v  verbose output");
        console.info.println("  -e  print stacktrace for all errors");
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
        console.info.println("  gav:net.oneandone:hellowar:1.0.0  (artifact stage)");
        console.info.println("  https://github.com/mlhartme/hellowar/trunk (source stage)");
        console.info.println();
        console.info.println("documentation:");
        console.info.println("  https://github.com/mlhartme/stool");
    }

    private Session session() throws IOException {
        if (session == null) {
            session = Session.load(logging, user, command, environment, console, invocationFile);
        }
        return session;
    }

    @Override
    public void invoke() throws Exception {
        printHelp();
    }
}
