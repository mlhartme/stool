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
import net.oneandone.stool.util.Lock;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Option;

import javax.naming.NoPermissionException;
import java.io.IOException;

public class Remove extends StageCommand {
    @Option("batch")
    private boolean batch;

    @Option("force")
    private boolean force;

    @Option("wrapper")
    private boolean wrappersOnly;

    public Remove(Session session) throws IOException {
        super(session);
    }

    public Remove(Session session, boolean batch, boolean force) throws IOException {
        super(session);
        this.batch = batch;
        this.force = force;
    }

    @Override
    public Lock lock() {
        return session.lock();
    }

    @Override
    public Lock stageLock(Stage stage) {
        return null;
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        stage.checkOwnership();

        if (!force) {
            checkCommitted(stage);
        }
        if (stage.state() == Stage.State.UP) {
            stage.stop(console);
        }
        if (!batch) {
            console.info.println("Ready to delete " + stage.getDirectory().getAbsolute() + "?");
            console.pressReturn();
        }

        stage.wrapper.deleteTree();
        if (wrappersOnly) {
            console.info.println("Removed wrapper for " + stage.getDirectory().getAbsolute());
        } else {
            stage.getDirectory().deleteTree();
            console.info.println("Removed " + stage.getDirectory().getAbsolute());
        }
        if (session.isSelected(stage)) {
            session.resetEnvironment();
        }

        session.bedroom.remove(session.gson, stage.getName());
    }
}
