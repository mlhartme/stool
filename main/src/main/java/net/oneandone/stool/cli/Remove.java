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

import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;

import java.io.IOException;

public class Remove extends StageCommand {
    private boolean batch;
    private boolean force;
    private boolean backstageOnly;

    public Remove(Session session, boolean batch, boolean force) {
        super(session, Mode.EXCLUSIVE, Mode.NONE, Mode.NONE);
        this.batch = batch;
        this.force = force;
        this.backstageOnly = false;
    }

    public void setBackstage(boolean backstageOnly) {
        this.backstageOnly = backstageOnly;
    }

    @Override
    public void doRun(Stage stage) throws Exception {
        stage.checkNotUp();
        stage.checkOwnership();
        if (!force) {
            if (!stage.isCommitted()) {
                throw new IOException("checkout has modifications - aborted.\nYou may run with -force");
            }
        }
        if (!batch) {
            console.info.println("Ready to delete " + stage.getDirectory().getAbsolute() + "?");
            console.pressReturn();
        }
        if (backstageOnly) {
            stage.backstage.deleteTree();
            console.info.println("Removed backstage for " + stage.getDirectory().getAbsolute());
        } else {
            stage.getDirectory().deleteTree();
            console.info.println("Removed " + stage.getDirectory().getAbsolute());
        }
        session.backstageLink(stage.getName()).deleteTree();
        session.bedroom.remove(session.gson, stage.getName());
    }
}
