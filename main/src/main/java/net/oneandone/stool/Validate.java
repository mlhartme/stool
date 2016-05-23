/**
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
package net.oneandone.stool;

import net.oneandone.stool.configuration.Until;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.User;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.util.Mailer;
import net.oneandone.stool.util.Processes;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;

import javax.mail.MessagingException;
import javax.naming.NamingException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Validate extends StageCommand {
    @Option("email")
    private boolean email;

    @Option("repair")
    private boolean repair;

    // data shared for all stages
    private Processes processes;
    private Report report;

    public Validate(Session session) {
        super(session, Mode.SHARED, Mode.EXCLUSIVE, Mode.EXCLUSIVE);
    }

    @Override
    public void doInvoke() throws Exception {
        processes = Processes.create(console.world);
        report = new Report();
        dns();
        locks();
        super.doInvoke();
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

    private void dns() throws IOException {
        String ip;
        String subDomain;
        ServerSocket socket;

        ip = digIp(session.configuration.hostname);
        if (ip.isEmpty()) {
            report.admin("missing dns entry for " + session.configuration.hostname);
        }

        // make sure that hostname points to this machine. Help to detect actually adding the name of a different machine
        try {
            socket = new ServerSocket(session.configuration.portLast, 50, InetAddress.getByName(ip));
            socket.close();
        } catch (IOException e) {
            report.admin("cannot open socket on machine " + session.configuration.hostname + ". Check the configured hostname.");
        }

        subDomain = digIp("foo." + session.configuration.hostname);
        if (subDomain.isEmpty() || !subDomain.endsWith(ip)) {
            report.admin("missing dns * entry for " + session.configuration.hostname + " (" + subDomain + ")");
        }
    }

    private void locks() throws IOException {
        for (Integer pid : session.lockManager.validate(processes, repair)) {
            if (repair) {
                report.admin("repaired locks: removed stale lock(s) for process id " + pid);
            } else {
                report.admin("detected stale locks for process id " + pid);
            }
        }

    }

    private String digIp(String name) throws Failure {
        Launcher dig;

        dig = new Launcher((FileNode) console.world.getWorking(), "dig", "+short", name);
        return dig.exec().trim();
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        tomcat(stage);
        until(stage);
    }

    //--

    public void until(Stage stage) throws IOException {
        Until until;

        until = stage.config().until;
        if (!until.isExpired()) {
            return;
        }

        report.user(stage, "stage has expired " + until);
        if (repair) {
            if (stage.runningTomcat() != null) {
                try {
                    new Stop(session).doInvoke(stage);
                    report.user(stage, "expired stage has been stopped");
                } catch (Exception e) {
                    report.user(stage, "expired stage failed to stop: " + e.getMessage());
                    e.printStackTrace(console.verbose);
                }
            }
            if (session.configuration.autoRemove >= 0) {
                if (stage.config().until.expiredDays() >= session.configuration.autoRemove) {
                    try {
                        // CAUTION: do not place this behind "remove", stage.owner() would fail
                        report.user(stage, "removing expired stage");
                        if (!stage.owner().equals(session.user)) {
                            new Chown(session, true, null).doInvoke(stage);
                        }
                        new Remove(session, true, true).doInvoke(stage);
                    } catch (Exception e) {
                        report.user(stage, "failed to remove expired stage: " + e.getMessage());
                        e.printStackTrace(console.verbose);
                    }
                } else {
                    report.user(stage, "CAUTION: This stage will be removed automatically in "
                            + (session.configuration.autoRemove - until.expiredDays()) + " day(s)");
                }
            }
        }
    }

    private void tomcat(Stage stage) throws IOException {
        String filePid;
        String psPid;

        filePid = stage.runningTomcat();
        psPid = processes.tomcatPid(stage.getBackstage());
        if (filePid == null) {
            filePid = "";
        }
        if (psPid == null) {
            psPid = "";
        }
        if (!filePid.equals(psPid)) {
            report.admin(stage, "Tomcat process mismatch: " + filePid + " vs " + psPid);
        }
    }

    //--

    public static class Report {
        private Map<String, List<String>> users;

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
            add(stage.owner(), prefix(stage) + problem);
        }

        public void console(Console console) {
            for (Map.Entry<String, List<String>> entry : users.entrySet()) {
                for (String msg : entry.getValue()) {
                    console.info.println(msg);
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
                try {
                    userobj = session.lookupUser(user);
                    email = (userobj == null ? session.configuration.admin : userobj.email);
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
