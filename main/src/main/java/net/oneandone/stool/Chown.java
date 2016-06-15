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

import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;

import java.io.IOException;

public class Chown extends StageCommand {
    private String userArgument;

    @Option("batch")
    private boolean batch;

    /**
     * Chown is a BaseCommand because it doesn't operate on the selected stage, but
     * if we consider locking, it makes much more sense to check if the stage which
     * should be chowned is currently locked.
     */
    public Chown(Session session) {
        this(session, false, null);
    }

    public Chown(Session session, boolean batch, String userArgument) {
        super(session, Mode.EXCLUSIVE, Mode.EXCLUSIVE, Mode.EXCLUSIVE);
        this.batch = batch;
        this.userArgument = userArgument;
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

        user = userArgument != null ? userArgument : session.user;
        if (stage.owner().contains(user)) {
            console.info.println("Nothing to do: stage " + stage.getName() + " already owned by " + user);
            return;
        }
        stage.checkNotUp();
        if (!stage.isCommitted()) {
            message(stage.getName() + ": checkout has modifications");
            newline();
            console.info.println("Those files will stay uncommitted and " + user + " will be the new owner of them.");
            if (!batch) {
                console.pressReturn();
            }
        }

        session.chown(stage, user);
        console.info.println("... " + user + " is now owner of " + stage.getName() + ".");

        stage = Stage.load(session, stage.backstage);
        newline();
        if (session.isSelected(stage)) {
            session.select(stage);
        }
    }
}
