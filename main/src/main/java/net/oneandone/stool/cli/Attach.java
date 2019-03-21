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
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.util.Project;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

public class Attach extends ProjectCommand {
    private final String stageName;

    public Attach(Session session, String stageName, FileNode project) {
        super(session, Mode.EXCLUSIVE, project);

        this.stageName = stageName;
    }

    @Override
    public void doRun(FileNode project) throws Exception {
        Project backstage;
        Reference reference;

        reference = server.resolveName(stageName);
        backstage = Project.lookup(project);
        if (backstage == null) {
            backstage = Project.create(project);
        }
        backstage.setAttached(reference);
    }
}
