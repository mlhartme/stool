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
import net.oneandone.stool.stage.Project;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;

public abstract class StageCommand extends ProjectCommand {
    public StageCommand(boolean withAutoRunning, Session session, Mode portsLock, Mode backstageLock, Mode directoryLock) {
        super(withAutoRunning, session, portsLock, backstageLock, directoryLock);
    }

    public boolean updateAvailable() {
        return false;
    }

    /** main method to perform this command */
    public final void doMain(Project project) throws Exception {
        doMain(project.stage);
    }

    /** main method to perform this command */
    public abstract void doMain(Stage stage) throws Exception;

    /** override this if your doMain method needs some finishing */
    public final void doFinish(Project project) throws Exception {
        doFinish(project.stage);
    }

    public void doFinish(Stage stage) throws Exception {
    }
}
