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
package net.oneandone.stool.cli;

import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.nio.file.Files;

public class Remove extends StageCommand {
    private final boolean batch;
    private final boolean force;
    private boolean backstageOnly;

    public Remove(Session session, boolean batch, boolean force) {
        super(true, session, Mode.EXCLUSIVE, Mode.EXCLUSIVE, Mode.EXCLUSIVE);
        this.batch = batch;
        this.force = force;
        this.backstageOnly = false;
    }

    public void setBackstage(boolean backstageOnly) {
        this.backstageOnly = backstageOnly;
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        boolean selected;
        FileNode dir;

        selected = session.isSelected(stage);
        stage.checkNotUp();
        stage.modify();
        if (!force) {
            if (!stage.isCommitted()) {
                throw new IOException("checkout has modifications - aborted.\nYou may run with -force");
            }
        }
        dir = backstageOnly ? session.backstageLink(stage.getId()).resolveLink() : stage.getDirectory();
        if (!batch) {
            console.info.println("Ready to delete " + dir.getAbsolute() + "?");
            console.pressReturn();
        }
        stage.wipeDocker(session.dockerEngine());

        // delete backstageLink first - to make sure no other stool invocation detects a stage backstage and wipes it
        Files.delete(session.backstageLink(stage.getId()).toPath());
        dir.deleteTree();
        session.bedroom.remove(session.gson, stage.getId());
        if (selected) {
            session.cd(stage.getDirectory().getParent());
        }
    }
}
