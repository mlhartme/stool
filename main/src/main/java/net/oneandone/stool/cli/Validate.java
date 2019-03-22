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
package net.oneandone.stool.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.User;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.util.Mailer;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;

import javax.mail.MessagingException;
import javax.naming.NamingException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.sql.Ref;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Validate extends StageCommand {
    private final boolean email;
    private final boolean repair;

    private Report report;

    public Validate(Session session, boolean email, boolean repair) {
        super(session, Mode.SHARED, Mode.EXCLUSIVE);
        this.email = email;
        this.repair = repair;
    }

    @Override
    public void doRun() throws Exception {
        report = new Report();
        docker();
        dns();
        session.logging.rotate();
        locks();
        super.doRun();
        if (report.isEmpty()) {
            console.info.println("validate ok");
        } else {
            report.console(console);
            if (email) {
                report.email(session);
            }
            console.info.println();
            console.info.println("validate failed");
        }
    }

    private void docker() {
        try {
            session.dockerEngine().imageList(Collections.emptyMap());
        } catch (IOException e) {
            report.admin("cannot access docker: " + e.getMessage());
            e.printStackTrace(console.verbose);
        }
    }

    private void dns() throws IOException {
        int port;
        String ip;
        String subDomain;
        ServerSocket socket;

        try {
            ip = digIp(session.configuration.hostname);
        } catch (Failure e) {
            report.admin("cannot validate dns entries: " + e.getMessage());
            return;
        }
        if (ip.isEmpty()) {
            report.admin("missing dns entry for " + session.configuration.hostname);
            return;
        }

        // make sure that hostname points to this machine. Help to detect actually adding the name of a different machine
        port = session.pool().temp();
        try {
            socket = new ServerSocket(port,50, InetAddress.getByName(session.configuration.hostname));
            socket.close();
        } catch (IOException e) {
            report.admin("cannot open socket on machine " + session.configuration.hostname + ", port " + port + ". Check the configured hostname.");
            e.printStackTrace(console.verbose);
        }

        subDomain = digIp("foo." + session.configuration.hostname);
        if (subDomain.isEmpty() || !subDomain.endsWith(ip)) {
            report.admin("missing dns * entry for " + session.configuration.hostname + " (" + subDomain + ")");
        }
    }

    private void locks() throws IOException {
        for (Integer pid : session.lockManager.validate(processes(), repair)) {
            if (repair) {
                report.admin("repaired locks: removed stale lock(s) for process id " + pid);
            } else {
                report.admin("detected stale locks for process id " + pid);
            }
        }

    }

    private String digIp(String name) throws Failure {
        Launcher dig;

        dig = new Launcher(world.getWorking(), "dig", "+short", name);
        return dig.exec().trim();
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        constraints(session.load(reference));
    }

    //--

    public void constraints(Stage stage) throws IOException {
        String message;

        try {
            stage.checkConstraints();
            return;
        } catch (ArgumentException e) {
            message = e.getMessage();
        }
        report.user(stage, message);
        if (repair) {
            if (!stage.dockerContainerList().isEmpty()) {
                try {
                    new Stop(session).doRun(stage.reference);
                    report.user(stage, "stage has been stopped");
                } catch (Exception e) {
                    report.user(stage, "stage failed to stop: " + e.getMessage());
                    e.printStackTrace(console.verbose);
                }
            }
            if (session.configuration.autoRemove >= 0 && stage.configuration.expire.expiredDays() >= 0) {
                if (stage.configuration.expire.expiredDays() >= session.configuration.autoRemove) {
                    try {
                        report.user(stage, "removing expired stage");
                        new Remove(session, true, true).doRun(stage.reference);
                    } catch (Exception e) {
                        report.user(stage, "failed to remove expired stage: " + e.getMessage());
                        e.printStackTrace(console.verbose);
                    }
                } else {
                    report.user(stage, "CAUTION: This stage will be removed automatically in "
                            + (session.configuration.autoRemove - stage.configuration.expire.expiredDays()) + " day(s)");
                }
            }
        }
    }

    //--

    public static class Report {
        /** key is a userid or an emails address */
        private final Map<String, List<String>> users;

        public Report() {
            this.users = new HashMap<>();
        }

        public void admin(String problem) {
            admin(null, problem);
        }

        public void admin(Stage stage, String problem) {
            add(null, prefix(stage) + problem);
        }

        public void user(Stage stage, String problem) throws IOException {
            Set<String> done;
            String login;

            done = new HashSet<>();
            for (String user : stage.configuration.notify) {
                switch (user) {
                    case StageConfiguration.NOTIFY_LAST_MODIFIED_BY:
                        login = stage.lastModifiedBy();
                        break;
                    case StageConfiguration.NOTIFY_CREATED_BY:
                        login = stage.createdBy();
                        break;
                    default:
                        login = user;
                }
                if (done.add(login)) {
                    add(login, prefix(stage) + problem);
                }
            }
        }

        public void console(Console console) {
            List<String> done;

            // Console output as to show all messages, no matter to which user is to be notified about it.
            // So we have to remove duplicates: the same message is generally added to more than one users
            // (an alternative implementation would map messages to user lists, but this makes
            // it more difficult to collect fetch the messages to be sent to individual users)
            done = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : users.entrySet()) {
                for (String msg : entry.getValue()) {
                    if (!done.contains(msg)) {
                        done.add(msg);
                        console.info.println(msg);
                    }
                }
            }
        }

        public void email(Session session) throws NamingException, MessagingException {
            String hostname;
            Mailer mailer;
            Console console;
            String user;
            String email;
            String body;

            hostname = session.configuration.hostname;
            mailer = session.configuration.mailer();
            console = session.console;
            for (Map.Entry<String, List<String>> entry : users.entrySet()) {
                user = entry.getKey();
                body = Separator.RAW_LINE.join(entry.getValue());
                email = email(session, user);
                if (email == null) {
                    console.error.println("cannot send email, there's nobody to send it to.");
                } else {
                    console.info.println("sending email to " + email);
                    mailer.send("stool@" + hostname, new String[] { email }, "Validation of your stage(s) on " + hostname + " failed", body);
                }
            }
        }

        private static String email(Session session, String user) throws NamingException {
            User userobj;
            String email;

            if (user == null) {
                email = session.configuration.admin;
            } else {
                if (user.contains("@")) {
                    return user;
                }
                try {
                    userobj = session.users.byLogin(user);
                    email = (userobj.isGenerated() ? session.configuration.admin : userobj.email);
                } catch (UserNotFound e) {
                    email = session.configuration.admin;
                }
            }
            return email.isEmpty() ? null : email;
        }

        public boolean isEmpty() {
            return users.isEmpty();
        }

        //--

        private static String prefix(Stage stage) {
            return stage == null ? "" : stage.getName() + ": ";
        }

        private void add(String user, String problem) {
            List<String> problems;

            problems = users.get(user);
            if (problems == null) {
                problems = new ArrayList<>();
                users.put(user, problems);
            }
            problems.add(problem);
        }
    }
}
