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

import net.oneandone.stool.cli.Caller;
import net.oneandone.stool.core.Configuration;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.core.Stage;
import net.oneandone.stool.util.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.io.IOException;
import java.util.List;

@Component
public class ScheduledTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTask.class);

    private final Configuration configuration;

    @Autowired
    public ScheduledTask(Configuration configuration) {
        this.configuration = configuration;
    }

    // second minute hour ...
    @Scheduled(cron = "0 4 2 * * *")
    public void validateAll() throws IOException, MessagingException {
        List<String> output;

        LOGGER.info("scheduled stage validation");
        try (Engine engine = Engine.createCluster(configuration.json)) {
            for (Stage stage : configuration.listAll(engine)) {
                LOGGER.info("validate " + stage.getName() + ":");
                output = new Validation(configuration, configuration.createUserManager() /* TODO */, engine,
                        new Caller("TODO", "TODO", "scheduled-task", null)).run(stage.getName(), !configuration.mailHost.isEmpty(), true);
                for (String line : output) {
                    LOGGER.info("  " + line);
                }
            }
        }
    }
}
