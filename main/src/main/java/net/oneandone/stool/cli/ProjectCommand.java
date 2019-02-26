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

public abstract class ProjectCommand extends SessionCommand {
    private final FileNode project;

    public ProjectCommand(Session session, Mode portsLock, FileNode project) {
        super(session, portsLock);
        this.project = project == null ? session.world.getWorking() : project;
    }

    @Override
    public void doRun() throws Exception {
        doRun(Project.load(project));
    }

    public abstract void doRun(Project project) throws Exception;
}