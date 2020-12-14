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

import net.oneandone.stool.server.Server;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.server.Type;
import net.oneandone.stool.server.settings.Expire;
import net.oneandone.stool.server.Stage;
import net.oneandone.stool.server.users.User;
import net.oneandone.stool.server.users.UserNotFound;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import javax.mail.MessagingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Validation {
    private final Server server;
    private final Engine engine;

    public Validation(Server server, Engine engine) {
        this.server = server;
        this.engine = engine;
    }

    public List<String> run(String name, boolean email, boolean repair) throws IOException, MessagingException {
        List<String> report;
        Stage stage;

        stage = server.load(engine, name);
        report = new ArrayList<>();
        doRun(stage, report, repair);
        Server.LOGGER.info("Validation done (" + report.size() + " lines report)");
        for (String line : report) {
            Server.LOGGER.info("  " + line);
        }
        if (email && !report.isEmpty()) {
            email(name, stage.notifyLogins(), report);
        }
        return report;
    }

    private void doRun(Stage stage, List<String> report, boolean repair) {
        Expire expire;

        expire = stage.getMetadataExpire();
        if (expire.isExpired()) {
            report.add("Stage expired " + expire + ". To start it, you have to adjust the 'expire' date.");
        }
        if (repair) {
            try {
                stage.publish(null, Strings.toMap(Type.VALUE_REPLICAS, "0"));
                report.add("replicas set to 0");
            } catch (Exception e) {
                report.add("replicas change failed: " + e.getMessage());
                Server.LOGGER.debug(e.getMessage(), e);
            }
            if (server.settings.autoRemove >= 0 && expire.expiredDays() >= 0) {
                if (expire.expiredDays() >= server.settings.autoRemove) {
                    try {
                        report.add("removing expired stage");
                        stage.uninstall(engine);
                    } catch (Exception e) {
                        report.add("failed to remove expired stage: " + e.getMessage());
                        Server.LOGGER.debug(e.getMessage(), e);
                    }
                } else {
                    report.add("CAUTION: This stage will be removed automatically in "
                            + (server.settings.autoRemove - expire.expiredDays()) + " day(s)");
                }
            }
        }
    }

    private void email(String name, Set<String> users, List<String> report) throws MessagingException {
        String fqdn;
        Mailer mailer;
        String email;
        String body;

        fqdn = server.settings.fqdn;
        mailer = server.settings.mailer();
        for (String user : users) {
            body = Separator.RAW_LINE.join(report);
            email = email(user);
            if (email == null) {
                Server.LOGGER.error("cannot send email, there's nobody to send it to.");
            } else {
                Server.LOGGER.info("sending email to " + email);
                mailer.send("stool@" + fqdn, new String[] { email },
                        "stage validation failed: " + name + "@" + fqdn, body);
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
            email = userobj.email == null ? server.settings.admin : userobj.email;
        } catch (UserNotFound e) {
            email = server.settings.admin;
        }
        return email.isEmpty() ? null : email;
    }
}
