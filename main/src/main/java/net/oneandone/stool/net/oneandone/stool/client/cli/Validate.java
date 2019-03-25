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
package net.oneandone.stool.net.oneandone.stool.client.cli;

import net.oneandone.inline.Console;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.net.oneandone.stool.server.stage.Reference;
import net.oneandone.stool.net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.util.Server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Validate extends StageCommand {
    private final boolean email;
    private final boolean repair;

    private Report report;

    public Validate(Server server, boolean email, boolean repair) {
        super(server);
        this.email = email;
        this.repair = repair;
    }

    @Override
    public void doRun() throws Exception {
        report = new Report();

        server.validateServer(report);
        super.doRun();
        if (report.isEmpty()) {
            console.info.println("validate ok");
        } else {
            report.console(console);
            if (email) {
                server.email(report);
            }
            console.info.println();
            console.info.println("validate failed");
        }
    }


    @Override
    public void doMain(Reference reference) throws Exception {
        server.validateState(reference, report, repair);
    }

    //--

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
}
