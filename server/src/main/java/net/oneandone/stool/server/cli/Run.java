package net.oneandone.stool.server.cli;

import net.oneandone.inline.Console;
import net.oneandone.stool.server.Main;
import org.springframework.boot.SpringApplication;

public class Run {
    public Run(Console console) {
        if (console.getVerbose()) {
            System.setProperty("loglevel", "DEBUG");
        }
    }

    public void run() {
        SpringApplication.run(Main.class, new String[] {});
    }
}
