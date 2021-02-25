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
package net.oneandone.stool.util;

import net.oneandone.stool.cli.Caller;
import net.oneandone.stool.core.Configuration;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.core.Dependencies;
import net.oneandone.stool.core.Stage;
import net.oneandone.stool.server.users.User;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.server.users.UserNotFound;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.MessagingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Validation {
    private static final Logger LOGGER = LoggerFactory.getLogger(Validation.class);

    private final String kubeContext;
    private final Configuration configuration;
    private final UserManager userManager;
    private final Engine engine;
    private final Caller caller;

    public Validation(String kubeContext, Configuration configuration, UserManager userManager, Engine engine, Caller caller) {
        this.kubeContext = kubeContext;
        this.configuration = configuration;
        this.userManager = userManager;
        this.engine = engine;
        this.caller = caller;
    }

    public List<String> run(String name, boolean email, boolean repair) throws IOException, MessagingException {
        List<String> report;
        Stage stage;

        stage = configuration.load(engine, name);
        report = new ArrayList<>();
        doRun(stage, report, repair);
        LOGGER.info("Validation done (" + report.size() + " lines report)");
        for (String line : report) {
            LOGGER.info("  " + line);
        }
        if (email && !report.isEmpty()) {
            email(name, stage.notifyLogins(), report);
        }
        return report;
    }

    private void doRun(Stage stage, List<String> report, boolean repair) {
        Expire expire;

        expire = stage.getMetadataExpire();
        if (!expire.isExpired()) {
            return;
        }
        report.add("Stage expired: " + expire + ": " + stage);
        if (!repair) {
            return;
        }
        try {
            stage.setValues(caller, kubeContext, engine, Strings.toMap(Dependencies.VALUE_REPLICAS, "0"));
            report.add("replicas set to 0");
        } catch (Exception e) {
            report.add("replicas change failed: " + e.getMessage());
            LOGGER.debug(e.getMessage(), e);
        }
        if (configuration.autoRemove >= 0 && expire.expiredDays() >= 0) {
            if (expire.expiredDays() >= configuration.autoRemove) {
                try {
                    report.add("removing expired stage");
                    stage.uninstall(kubeContext, engine);
                } catch (Exception e) {
                    report.add("failed to remove expired stage: " + e.getMessage());
                    LOGGER.debug(e.getMessage(), e);
                }
            } else {
                report.add("CAUTION: This stage will be removed automatically in "
                        + (configuration.autoRemove - expire.expiredDays()) + " day(s)");
            }
        }
    }

    private void email(String name, Set<String> users, List<String> report) throws MessagingException {
        String fqdn;
        Mailer mailer;
        String email;
        String body;

        fqdn = configuration.fqdn;
        mailer = configuration.mailer();
        for (String user : users) {
            body = Separator.RAW_LINE.join(report);
            email = email(user);
            if (email == null) {
                LOGGER.error("cannot send email, there's nobody to send it to.");
            } else {
                LOGGER.info("sending email to " + email);
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
            userobj = userManager.byLogin(user);
            email = userobj.email == null ? configuration.admin : userobj.email;
        } catch (UserNotFound e) {
            email = configuration.admin;
        }
        return email.isEmpty() ? null : email;
    }
}
