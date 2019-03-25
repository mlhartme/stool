package net.oneandone.stool.client.cli;

import net.oneandone.inline.Console;
import net.oneandone.stool.server.configuration.StageConfiguration;
import net.oneandone.stool.server.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Report {
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
