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
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.Slf4jOutputStream;
import net.oneandone.sushi.cli.Child;
import net.oneandone.sushi.cli.Cli;
import net.oneandone.sushi.cli.Command;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.InputLogStream;
import net.oneandone.sushi.io.MultiOutputStream;
import net.oneandone.sushi.io.PrefixWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.UUID;

public class Main extends Cli implements Command {
    public static void main(String[] args) throws IOException {
        MDC.put("UUID", UUID.randomUUID().toString());
        PrintWriter info;
        PrintWriter error;
        InputStream input;
        World world;
        Environment environment;
        Console console;
        Logger inputLogger;

        world = new World();
        environment = Environment.loadSystem();
        environment.stoolHome(world).checkDirectory();

        fixLogPermissions(world, environment);
        inputLogger = LoggerFactory.getLogger("IN");
        Logger errorLogger = LoggerFactory.getLogger("OUT.ERROR");
        Logger infoLogger = LoggerFactory.getLogger("OUT.INFO");
        info = new PrintWriter(MultiOutputStream.createTeeStream(System.out, new Slf4jOutputStream(infoLogger, false)), true);
        error = new PrintWriter(MultiOutputStream.createTeeStream(System.err, new Slf4jOutputStream(errorLogger, false)), true);

        // empty prefix is replaced by stage commands when iterating multiple stages
        info = new PrefixWriter(info);
        error = new PrefixWriter(error);

        input = new InputLogStream(System.in, new Slf4jOutputStream(inputLogger, true));
        console = new Console(world, info, error, input);

        System.exit(new Main(environment, console, inputLogger, args).run(args));
    }

    public static final String INBOX = "inbox";
    private String[] stoolArgs;
    private Environment environment;
    private Session session;
    private Logger inputLogger;
    @Option("invocation")
    private FileNode invocationFile;

    public Main(Environment environment, Console console, Logger inputlogger, String[] stoolArgs) {
        super(console);
        this.environment = environment;
        this.session = null;
        this.inputLogger = inputlogger;
        this.stoolArgs = stoolArgs;
    }

    //TODO: this is just a silly hack. Cause i don't know why sometimes the stool.log has 644.
    //TODO: crashing if stool_home is not set
    private static void fixLogPermissions(World world, Environment environment) throws IOException {
        FileNode home;

        home = environment.stoolHome(world);
        try {
            if (home.join("logs", "stool.log").exists()) {
                Files.stoolFile(home.join("logs", "stool.log"));
            } else {
                Files.stoolFile(home.join("logs", "stool.log").writeString(""));
            }
        } catch (IOException e) {
            home.join("logs", "stool.log").deleteFile();
            Files.stoolFile(home.join("logs", "stool.log").writeString(""));
        }
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

    @Child("resolve")
    public Resolve resolve() throws IOException {
        return new Resolve(console);
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
        console.info.println("  import [-max <n>] <directory>* ......  turns existing applications into a stage");
        console.info.println("  select [<stage>|none]  ..............  use <stage> for stage commands");
        console.info.println("stage commands");
        console.info.println("  config {<key>[=<val>]}  .............  get/set stage properties;");
        console.info.println("  .....................................  invoke without arguments to get a list of available keys");
        console.info.println("  status  .............................  prints stage information (status & purpose)");
        console.info.println("  build  ..............................  builds the stage; for convenience only -- you're free to build ");
        console.info.println(
          "  .....................................  your application as you normally do, stool configure the necessary");
        console.info.println("  .....................................  environment variables");
        console.info.println("  refresh [-build|-stop|-own|-debug] ..  update the stage, with optional building;");
        console.info.println("  .....................................  restart and chowning when necessary");
        console.info.println("  start [-debug|-suspend|-tail] .......  starts the stage, and optionally tails catalina.out");
        console.info.println("  stop [-sleep|-crashed]...............  stops the stage");
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
        console.info.println("selection options for stage commands");
        console.info.println("  -stage <name{,name,...}>");
        console.info.println("  -all");
        console.info.println("  -all-state [down|sleep|up]");
        console.info.println("  -all-owner <owner>");
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
        console.info.println("exmaple urls: ");
        console.info.println("  https://svn.code.sf.net/p/pustefix/code/tags/pustefixframework-0.18.84/pustefix-samples/pustefix-sample1");
        console.info.println("  gav:org.pustefixframework:pustefix-sample1:0.18.84");
        console.info.println();
        console.info.println("documentation:");
        console.info.println("  https://github.com/mlhartme/stool");
    }

    private Session session() throws IOException {
        if (session == null) {
            session = Session.load(environment, console, invocationFile);
        }
        MDC.put("stage", session.getSelectedStageName());
        if (stoolArgs != null) {
            StringBuilder stoolarguments;
            stoolarguments = new StringBuilder("stool");
            for (String arg : Arrays.copyOfRange(stoolArgs, 2, stoolArgs.length)) {
                stoolarguments.append(' ').append(arg);
            }
            if (inputLogger != null) {
                inputLogger.info(stoolarguments.toString());
            }
        }

        return session;
    }

    @Override
    public void invoke() throws Exception {
        printHelp();
    }
}
