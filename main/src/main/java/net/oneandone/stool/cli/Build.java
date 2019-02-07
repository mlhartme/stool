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
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;

import java.util.ArrayList;
import java.util.List;

public class Build extends StageCommand {
    private final boolean here;
    private final List<String> command;

    public Build(Session session) {
        this(session, false, new ArrayList<>());
    }

    public Build(Session session, boolean here, List<String> command) {
        super(true, session, Mode.NONE, Mode.SHARED, Mode.EXCLUSIVE);
        this.here = here;
        this.command = command;
    }

    @Override
    public void doMain(Project project) throws Exception {
        FileNode directory;
        Launcher launcher;

        project.modify();
        project.getStage().checkNotUp();
        if (command.isEmpty()) {
            command.addAll(Separator.SPACE.split(project.getBuild()));
        }
        if (here) {
            directory = world.getWorking();
        } else {
            directory = project.getDirectory();
        }
        launcher = project.launcher();
        launcher.dir(directory);
        console.info.println("[" + directory + "] " + command);
        launcher.args(command);
        launcher.exec(console.info);
    }
}
