package net.oneandone.stool.server.cli;

import net.oneandone.stool.server.Main;
import org.springframework.boot.SpringApplication;

public class Run {
    public void run() {
        SpringApplication.run(Main.class, new String[] {});
    }
}
