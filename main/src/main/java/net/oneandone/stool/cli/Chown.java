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
package net.oneandone.stool.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;

public class Chown extends StageCommand {
    private boolean batch;
    private String userArgument;

    public Chown(Session session, boolean batch, String userArgument) {
        super(true, false, session, Mode.EXCLUSIVE, Mode.EXCLUSIVE, Mode.EXCLUSIVE);
        if ("root".equals(userArgument)) {
            throw new ArgumentException("Root does not want to own stages.");
        }
        this.userArgument = userArgument;
        this.batch = batch;
    }

    @Override
    public void doRun(Stage stage) throws Exception {
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
        newline();
    }
}
