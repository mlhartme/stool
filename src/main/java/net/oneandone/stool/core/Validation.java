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
package net.oneandone.stool.core;

import net.oneandone.stool.cli.Caller;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.server.users.User;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.server.users.UserNotFound;
import net.oneandone.stool.util.Expire;
import net.oneandone.stool.util.Mailer;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.MessagingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Validation {
    private static final Logger LOGGER = LoggerFactory.getLogger(Validation.class);

    private final String kubeContext;
    private final LocalSettings localSettings;
    private final UserManager userManager;
    private final Engine engine;
    private final Caller caller;

    public Validation(String kubeContext, LocalSettings localSettings, UserManager userManager, Engine engine, Caller caller) {
        this.kubeContext = kubeContext;
        this.localSettings = localSettings;
        this.userManager = userManager;
        this.engine = engine;
        this.caller = caller;
    }

    public List<String> run(String name, boolean email, boolean repair) throws IOException, MessagingException {
        List<String> report;
        Stage stage;

        stage = localSettings.load(engine, name);
        report = new ArrayList<>();
        doRun(stage, report, repair);
        LOGGER.info("Validation done (" + report.size() + " lines report)");
        for (String line : report) {
            LOGGER.info("  " + line);
        }
        if (email && !report.isEmpty()) {
            email(name, new HashSet<>(stage.getMetadataContact()), report);
        }
        return report;
    }

    private void doRun(Stage stage, List<String> report, boolean repair) {
        Expire expire;

        expire = stage.getMetadataExpireOpt();
        if (expire == null || !expire.isExpired()) {
            return;
        }
        report.add("Stage expired: " + expire + ": " + stage);
        if (!repair) {
            return;
        }
        if (stage.variableOpt(Dependencies.VALUE_REPLICAS) == null) {
            report.add("no replicas variable -- cannot stop expired stage");
        } else {
            try {
                stage.setValues(caller, kubeContext, engine, Strings.toMap(Dependencies.VALUE_REPLICAS, "0"));
                report.add("replicas set to 0");
            } catch (Exception e) {
                report.add("replicas change failed: " + e.getMessage());
                LOGGER.debug(e.getMessage(), e);
            }
        }
        if (localSettings.autoRemove < 0) {
            return;
        }
        if (expire.expiredDays() >= localSettings.autoRemove) {
            try {
                report.add("deleting expired stage");
                stage.uninstall(kubeContext, engine);
            } catch (Exception e) {
                report.add("failed to remove expired stage: " + e.getMessage());
                LOGGER.debug(e.getMessage(), e);
            }
        } else {
            report.add("CAUTION: This stage will be removed automatically in "
                    + (localSettings.autoRemove - expire.expiredDays()) + " day(s)");
        }
    }

    private void email(String name, Set<String> users, List<String> report) throws MessagingException {
        String fqdn;
        Mailer mailer;
        String email;
        String body;

        fqdn = localSettings.fqdn;
        mailer = localSettings.mailer();
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
            email = userobj.email == null ? localSettings.admin : userobj.email;
        } catch (UserNotFound e) {
            email = localSettings.admin;
        }
        return email.isEmpty() ? null : email;
    }
}
