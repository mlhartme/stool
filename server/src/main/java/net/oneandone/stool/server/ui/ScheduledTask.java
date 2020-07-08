/*
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
package net.oneandone.stool.server.ui;

import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.kubernetes.Engine;
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
        Registry registry;

        Server.LOGGER.info("scheduled stage validation");
        try (Engine engine = Engine.createFromCluster()) {
            registry = server.createRegistry();
            for (Stage stage : server.listAll(engine)) {
                Server.LOGGER.info("validate " + stage.getName() + ":");
                output = new Validation(server, engine, registry).run(stage.getName(), !server.configuration.mailHost.isEmpty(), true);
                for (String line : output) {
                    Server.LOGGER.info("  " + line);
                }
            }
        }
    }
}
