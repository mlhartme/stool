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

        builder = new ProcessBuilder();
        builder.directory(null /* use current directory */);
        builder.command("man", command == null ? "stool" : "stool-" + command);
        builder.inheritIO();
        process = builder.start();
        process.waitFor();
    }
}
