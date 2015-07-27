package net.oneandone.stool;

import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Command;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.cli.Remaining;

public class Help implements Command {
    private final Console console;

    private String command = null;

    public Help(Console console) {
        this.console = console;
    }

    @Remaining
    public void remaining(String arg) {
        if (command != null) {
            throw new ArgumentException("too many arguments");
        }
        command = arg;
    }

    @Override
    public void invoke() throws Exception {
        ProcessBuilder builder;
        Process process;

        if (command == null) {
            printHelp();
        } else {
            builder = new ProcessBuilder();
            builder.directory(null /* use current directory */);
            builder.command("man", "stool-" + command);
            builder.inheritIO();
            process = builder.start();
            process.waitFor();
        }
    }

    public void printHelp() {
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
        console.info.println("  Invoke 'stool help <command>' to get documentation for individual commands.");
    }

}
