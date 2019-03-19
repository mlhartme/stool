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
import net.oneandone.stool.util.Project;
import net.oneandone.stool.util.Session;

public class Remove extends StageCommand {
    private final boolean batch;
    private final boolean stop;

    public Remove(Session session, boolean batch, boolean stop) {
        super(session, Mode.EXCLUSIVE, Mode.EXCLUSIVE);
        this.batch = batch;
        this.stop = stop;
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        Project project;

        if (stop && stage.state() == Stage.State.UP) {
            new Stop(session).doRun(stage);
        }
        stage.checkNotUp();
        // TODO: remove stage link ...
        if (!batch) {
            console.info.println("Ready to delete " + stage.directory.getAbsolute() + "?");
            console.pressReturn();
        }
        stage.wipeDocker(session.dockerEngine());

        stage.directory.deleteTree();

        project = Project.lookup(session.world.getWorking());
        if (project != null) {
            project.removeBackstage();
        }
    }
}
