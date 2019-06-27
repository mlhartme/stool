package net.oneandone.stool.server.ui;

import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.util.Validation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.io.IOException;
import java.util.List;

@Component
public class ScheduledTask {
    private final Server server;

    @Autowired
    public ScheduledTask(Server server) {
        this.server = server;
    }

    // second minute hour ...
    @Scheduled(cron = "0 4 2 * * *")
    public void validateAll() throws IOException, MessagingException {
        List<String> output;

        Server.LOGGER.info("scheduled stage validation");
        try (Engine engine = Engine.create()) {
            for (Stage stage : server.listAll()) {
                Server.LOGGER.info("validate " + stage.getName() + ":");
                output = new Validation(server, engine).run(stage.getName(), !server.configuration.mailHost.isEmpty(), true);
                for (String line : output) {
                    Server.LOGGER.info("  " + line);
                }
            }
        }
    }
}
