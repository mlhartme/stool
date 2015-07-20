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
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.User;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.util.Mailer;
import net.oneandone.stool.util.Processes;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.util.Separator;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Validate extends StageCommand {
    @Option("email")
    private boolean email;

    @Option("stop")
    private boolean stop;


    // data shared for all stages
    private Mailer mailer;
    private String hostname;
    private Processes processes;


    public Validate(Session session) throws IOException {
        super(session);
    }
    private static void daemons(Stage stage, Processes processes, List<String> problems) throws IOException {
        tomcat(stage, processes, problems);
    }

    private static void tomcat(Stage stage, Processes processes, List<String> problems) throws IOException {
        String filePid;
        String psPid;

        filePid = stage.runningTomcat();
        psPid = processes.tomcatPid(stage.getWrapper());
        if (filePid == null) {
            filePid = "";
        }
        if (psPid == null) {
            psPid = "";
        }
        if (!filePid.equals(psPid)) {
            problems.add("Tomcat process mismatch: " + filePid + " vs " + psPid);
        }
    }

    @Override
    public void doInvoke() throws Exception {
        hostname = session.configuration.hostname;
        header("validate " + hostname);
        processes = Processes.create(console.world);
        if (email) {
            mailer = new Mailer(session.configuration.mailHost,
                    session.configuration.mailUsername, session.configuration.mailPassword);
        }
        super.doInvoke();
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        List<String> problems;
        String[] stageEmails;
        boolean wasStopped;

        problems = new ArrayList<>();
        daemons(stage, processes, problems);
        until(stage.config().until, problems);
        if (problems.isEmpty()) {
            message("ok");
        } else {
            message("failed:");
            for (String problem : problems) {
                message("  " + problem);
            }
            wasStopped = false;
            if (stop && stage.runningTomcat() != null) {
                try {
                    new Stop(session).doInvoke(stage);
                    wasStopped = true;
                } catch (Exception e) {
                    message("stop failed:");
                    e.printStackTrace(console.error);
                }
            }
            if (session.configuration.autoRemove > -1
              && stage.config().until.expiredDays() >= session.configuration.autoRemove) {
                if (stage.state() == Stage.State.UP) {
                    new Stop(session).doInvoke(stage);
                }
                if (!stage.owner().equals(session.user)) {
                    new Chown(session, true).doInvoke(stage);
                }
                new Remove(session, true, true).doInvoke(stage);
                message("Stage has been deleted.");
                return;
            }
            if (mailer != null) {
                stageEmails = stageEmails(stage);
                if (stageEmails.length == 0) {
                    message("cannot send email, there's nobody to send it to");
                } else {
                    message("sending email to " + Separator.SPACE.join(stageEmails));
                    mailer.send("stool@" + hostname, stageEmails,
                      "Validation for stage " + stage.getName() + "." + hostname + " failed",
                      body(problems, (wasStopped ? "The stage has been stopped.\n" : "")));
                }
            }
        }
    }

    //--
    private String body(List<String> problems, String stopped) {
        StringBuilder body;

        body = new StringBuilder();
        body.append("Validation found ").append(problems.size()).append(" problem").append(problems.size() == 1 ? "" : "s").append("\n\n");
        for (String problem : problems) {
            body.append("     ").append(problem).append("\n");
        }
        body.append("\nPlease fix this.").append("\n");
        body.append(stopped).append("\n");
        return body.toString();
    }

    public void until(Until until, List<String> problems) {
        StringBuilder problem;

        if (until.isExpired()) {
            problem = new StringBuilder();
            problem.append("stage has expired ").append(until).append(". Adjust the 'until' date or remove it:\n");
            if (session.configuration.autoRemove > -1) {
                problem.append("CAUTION: This stage will be removed automatically in " + (session.configuration.autoRemove - until.expiredDays()) + " day(s)");
            }
            problems.add(problem.toString());
        }
    }

    private String[] stageEmails(Stage stage) throws IOException, NamingException {
        String owner;
        User user;

        owner = stage.owner();
        try {
            user = session.lookupUser(owner);
        } catch (UserNotFound e) {
            owner = session.configuration.contactAdmin;
            if (owner.isEmpty()) {
                return new String[]{};
            }
            return new String[]{ owner };
        }
        return new String[]{ user.email };
    }
}
