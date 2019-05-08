package net.oneandone.stool.server.util;

import net.oneandone.stool.server.ArgumentException;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.users.User;
import net.oneandone.stool.server.users.UserNotFound;
import net.oneandone.sushi.util.Separator;

import javax.mail.MessagingException;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Validation {
    private final Server server;

    public Validation(Server server) {
        this.server = server;
    }

    public List<String> run(String name, boolean email, boolean repair) throws IOException, MessagingException, NamingException {
        List<String> report;
        Stage stage;

        stage = server.load(name);
        report = new ArrayList<>();
        validateStage(stage, report, repair);
        if (email) {
            email(stage.notifyLogins(), report);
        }
        return report;
    }

    private void validateStage(Stage stage, List<String> report, boolean repair) throws IOException {
        String message;

        try {
            stage.checkExpired();
            stage.checkDiskQuota(server.dockerEngine());
            return;
        } catch (ArgumentException e) {
            message = e.getMessage();
        }
        report.add(message);
        if (repair) {
            if (!stage.dockerRunningContainerList().isEmpty()) {
                try {
                    stage.stop(new ArrayList<>());
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
                        stage.remove();
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


    private void email(Set<String> users, List<String> report) throws MessagingException {
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
                mailer.send("stool@" + hostname, new String[] { email }, "Validation of your stage(s) on " + hostname + " failed", body);
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
