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

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Role;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;

import java.io.IOException;

public class Chown extends StageCommand {
    private String userArgument;

    @Option("stop")
    private boolean stop;

    @Option("batch")
    private boolean batch;

    /**
     * Chown is a BaseCommand because it doesn't operate on the selected stage, but
     * if we consider locking, it makes much more sense to check if the stage which
     * should be chowned is currently locked.
     */
    public Chown(Session session) throws IOException {
        super(session);
    }

    public Chown(Session session, boolean batch) throws IOException {
        super(session);
        this.batch = batch;
    }

    @Remaining
    public void user(String user) {
        if (userArgument != null) {
            throw new ArgumentException("too many users");
        }
        if ("root".equals(user)) {
            throw new ArgumentException("Root does not want to own stages.");
        }
        this.userArgument = user;
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        String user;
        boolean startAgain;

        user = userArgument != null ? userArgument : session.user;
        if (!session.configuration.security.isLocal()
          && stage.getName().equals(Overview.OVERVIEW_NAME) && Role.isAdmin(session.configuration)) {
            console.info.println("You're not allowed to do this. This incident will be reported.");
        }

        if (stage.owner().contains(user)) {
            console.info.println("Nothing to do: stage " + stage.getName() + " already owned by " + user);
            return;
        }

        try {
            checkCommitted(stage);
        } catch (IOException e) {
            message(stage.getName() + ": checkout has modifications");
            newline();
            console.info.println("Those files will stay uncommitted and " + user + " will be the new owner of them.");
            if (!batch) {
                console.pressReturn();
            }
        }

        if (Stage.State.UP.equals(stage.state())) {
            try {
                stage.stop(console);
                startAgain = true;
            } catch (Exception e) {
                console.info.println("WARNING: stop failed: " + e.getMessage());
                e.printStackTrace(console.info);
                startAgain = false;
            }
        } else {
            startAgain = false;
        }

        session.chown(stage, user);
        console.info.println("... " + user + " is now owner of " + stage.getName() + ".");

        stage = Stage.load(session, stage.wrapper);
        newline();
        if (startAgain && !stop) {
            new Start(session, false, false).doInvoke(stage);
        }
        if (session.isSelected(stage)) {
            session.select(stage);
        }
    }
}
