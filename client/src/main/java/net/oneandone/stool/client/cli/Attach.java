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
package net.oneandone.stool.client.cli;

import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.Reference;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.List;

public class Attach extends ProjectCommand {
    private final String stage;

    public Attach(Globals globals, FileNode project, String stage) {
        super(globals, project);
        this.stage = stage;
    }

    @Override
    public void doRun(FileNode project) throws Exception {
        Project backstage;

        List<Reference> found;

        found = globals.servers().list(stage);
        switch (found.size()) {
            case 0:
                throw new IOException("no such stage: " + stage);
            case 1:
                break;
            default:
                throw new IOException("stage ambiguous: " + stage);
        }
        backstage = Project.lookup(project);
        if (backstage == null) {
            backstage = Project.create(project);
        }
        backstage.setAttached(found.get(0));
    }
}
