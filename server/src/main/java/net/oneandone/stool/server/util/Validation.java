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
package net.oneandone.stool.server.util;

import net.oneandone.stool.docker.Daemon;
import net.oneandone.stool.docker.Registry;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.users.User;
import net.oneandone.stool.server.users.UserNotFound;
import net.oneandone.sushi.util.Separator;

import javax.mail.MessagingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Validation {
    private final Server server;
    private final Engine engine;
    private final Daemon docker;
    private final Registry registry;

    public Validation(Server server, Engine engine, Daemon docker, Registry registry) {
        this.server = server;
        this.engine = engine;
        this.docker = docker;
        this.registry = registry;
    }

    public List<String> run(String name, boolean email, boolean repair) throws IOException, MessagingException {
        List<String> report;
        Stage stage;

        stage = server.load(name);
        report = new ArrayList<>();
        doRun(stage, report, repair);
        if (email && !report.isEmpty()) {
            email(name, stage.notifyLogins(), report);
        }
        return report;
    }

    private void doRun(Stage stage, List<String> report, boolean repair) throws IOException {
        try {
            stage.checkExpired();
            stage.checkDiskQuota(engine, registry);
            checkPorts(stage);
            return;
        } catch (ArgumentException e) {
            report.add(e.getMessage());
        }
        if (repair) {
            if (stage.runningPodOpt(engine) != null) {
                try {
                    stage.stop(engine, docker, registry);
                    report.add("stage has been stopped");
                } catch (Exception e) {
                    report.add("stage failed to stop: " + e.getMessage());
                    Server.LOGGER.debug(e.getMessage(), e);
                }
            }
            if (server.configuration.autoRemove >= 0 && stage.configuration.expire.expiredDays() >= 0) {
                if (stage.configuration.expire.expiredDays() >= server.configuration.autoRemove) {
                    try {
                        report.add("removing expired stage");
                        stage.remove(engine, docker, registry);
                    } catch (Exception e) {
                        report.add("failed to remove expired stage: " + e.getMessage());
                        Server.LOGGER.debug(e.getMessage(), e);
                    }
                } else {
                    report.add("CAUTION: This stage will be removed automatically in "
                            + (server.configuration.autoRemove - stage.configuration.expire.expiredDays()) + " day(s)");
                }
            }
        }
    }

    private void checkPorts(Stage stage) throws IOException {
        Ports internal;
        Ports external;

        internal = server.pool.stageOpt(stage.getName());
        external = server.configuration.loadPool(engine)
                .stageOpt(stage.getName());
        if (internal == null && external == null) {
            return;
        }
        if (internal == null || external == null || !internal.equals(external)) {
            throw new ArgumentException("ports mismatch:\n  internal: " + internal + "\n  external: " + external);
        }
    }


    private void email(String name, Set<String> users, List<String> report) throws MessagingException {
        String hostname;
        Mailer mailer;
        String email;
        String body;

        hostname = server.configuration.dockerHost;
        mailer = server.configuration.mailer();
        for (String user : users) {
            body = Separator.RAW_LINE.join(report);
            email = email(user);
            if (email == null) {
                Server.LOGGER.error("cannot send email, there's nobody to send it to.");
            } else {
                Server.LOGGER.info("sending email to " + email);
                mailer.send("stool@" + hostname, new String[] { email },
                        "stage validation failed: " + name + "@" + hostname, body);
            }
        }
    }

    private String email(String user) {
        User userobj;
        String email;

        if (user.contains("@")) {
            return user;
        }
        try {
            userobj = server.userManager.byLogin(user);
            email = userobj.email == null ? server.configuration.admin : userobj.email;
        } catch (UserNotFound e) {
            email = server.configuration.admin;
        }
        return email.isEmpty() ? null : email;
    }
}
