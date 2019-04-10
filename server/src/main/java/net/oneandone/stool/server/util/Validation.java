package net.oneandone.stool.server.util;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.server.configuration.StageConfiguration;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.users.User;
import net.oneandone.stool.server.users.UserNotFound;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;

import javax.mail.MessagingException;
import javax.naming.NamingException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Validation {
    public final World world;
    private final Server context;

    public Validation(Server context) {
        this.world = context.world;
        this.context = context;
    }

    public List<String> run(String stageClause, boolean email, boolean repair) throws IOException, MessagingException, NamingException {
        Report report;
        List<String> names;
        Map<String, IOException> problems;

        report = new Report();
        validateServer(report);
        problems = new HashMap<>();
        names = new ArrayList<>();
        for (Stage stage : context.list(PredicateParser.parse(stageClause), problems)) {
            names.add(stage.getName());
        }
        if (!problems.isEmpty()) {
            throw new IOException("cannot get stages: " + problems.toString());
        }
        for (String name : names) {
            validateStage(name, report, repair);
        }
        if (email) {
            email(report);
        }
        return report.messages();
    }

    private void validateStage(String name, Report report, boolean repair) throws IOException {
        Stage stage;
        String message;

        stage = context.load(name);
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
                    stage.stop(new ArrayList<>());
                    report.user(stage, "stage has been stopped");
                } catch (Exception e) {
                    report.user(stage, "stage failed to stop: " + e.getMessage());
                    Server.LOGGER.debug(e.getMessage(), e);
                }
            }
            if (context.configuration.autoRemove >= 0 && stage.configuration.expire.expiredDays() >= 0) {
                if (stage.configuration.expire.expiredDays() >= context.configuration.autoRemove) {
                    try {
                        report.user(stage, "removing expired stage");
                        stage.remove();
                    } catch (Exception e) {
                        report.user(stage, "failed to remove expired stage: " + e.getMessage());
                        Server.LOGGER.debug(e.getMessage(), e);
                    }
                } else {
                    report.user(stage, "CAUTION: This stage will be removed automatically in "
                            + (context.configuration.autoRemove - stage.configuration.expire.expiredDays()) + " day(s)");
                }
            }
        }
    }

    private void validateServer(Report report) throws IOException {
        validateDocker(report);
        validateDns(report);
    }


    private void email(Report report) throws MessagingException, NamingException {
        String hostname;
        Mailer mailer;
        String user;
        String email;
        String body;

        hostname = context.configuration.hostname;
        mailer = context.configuration.mailer();
        for (Map.Entry<String, List<String>> entry : report.users.entrySet()) {
            user = entry.getKey();
            body = Separator.RAW_LINE.join(entry.getValue());
            email = email(context, user);
            if (email == null) {
                Server.LOGGER.error("cannot send email, there's nobody to send it to.");
            } else {
                Server.LOGGER.info("sending email to " + email);
                mailer.send("stool@" + hostname, new String[] { email }, "Validation of your stage(s) on " + hostname + " failed", body);
            }
        }
    }

    private static String email(Server session, String user) throws NamingException {
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


    private void validateDocker(Report report) {
        try {
            context.dockerEngine().imageList();
        } catch (IOException e) {
            report.admin("cannot access docker: " + e.getMessage());
            Server.LOGGER.debug("cannot access docker", e);
        }
    }

    private void validateDns(Report report) throws IOException {
        int port;
        String ip;
        String subDomain;
        ServerSocket socket;

        try {
            ip = digIp(context.configuration.hostname);
        } catch (Failure e) {
            report.admin("cannot validate dns entries: " + e.getMessage());
            return;
        }
        if (ip.isEmpty()) {
            report.admin("missing dns entry for " + context.configuration.hostname);
            return;
        }

        // make sure that hostname points to this machine. Help to detect actually adding the name of a different machine
        port = context.pool().temp();
        try {
            socket = new ServerSocket(port,50, InetAddress.getByName(context.configuration.hostname));
            socket.close();
        } catch (IOException e) {
            report.admin("cannot open socket on machine " + context.configuration.hostname + ", port " + port + ". Check the configured hostname.");
            Server.LOGGER.debug("cannot open socket", e);
        }

        subDomain = digIp("foo." + context.configuration.hostname);
        if (subDomain.isEmpty() || !subDomain.endsWith(ip)) {
            report.admin("missing dns * entry for " + context.configuration.hostname + " (" + subDomain + ")");
        }
    }

    private String digIp(String name) throws Failure {
        Launcher dig;

        dig = new Launcher(context.world.getWorking(), "dig", "+short", name);
        return dig.exec().trim();
    }

    public static class Report {
        /** key is a userid or an emails address */
        public final Map<String, List<String>> users;

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

        public List<String> messages() {
            List<String> result;

            result = new ArrayList<>();
            // returns all messages, no matter to which user is to be notified about it;
            // not that we have to remove duplicates: the same message is generally added to more than one users
            // (an alternative implementation would map messages to user lists, but this makes
            // it more difficult to collect fetch the messages to be sent to individual users)
            for (Map.Entry<String, List<String>> entry : users.entrySet()) {
                for (String msg : entry.getValue()) {
                    if (!result.contains(msg)) {
                        result.add(msg);
                    }
                }
            }
            return result;
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
