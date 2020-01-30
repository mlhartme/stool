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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.client.App;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.Reference;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.List;

public class Attach extends ProjectCommand {
    private final String stage;
    private final String pathOpt;

    public Attach(Globals globals, FileNode project, String stage) {
        super(globals, project);

        int idx;

        idx = stage.indexOf("=");
        if (idx == -1) {
            this.stage = stage;
            this.pathOpt = null;
        } else {
            this.stage = stage.substring(0, idx);
            this.pathOpt = stage.substring(idx + 1);
        }
    }

    @Override
    public void doRun(FileNode project) throws Exception {
        Project backstage;
        List<FileNode> wars;
        String nameAndServer;

        backstage = Project.lookup(project);
        if (backstage == null) {
            backstage = Project.create(project);
        }
        if (pathOpt == null) {
            wars = backstage.wars();
            if (wars.isEmpty()) {
                throw new ArgumentException("no wars found - did you build your project?");
            }
            for (FileNode war : wars) {
                nameAndServer = Create.app(war) + "." + stage;
                checkStage(nameAndServer);
                backstage.addAttached(new App(checkStage(nameAndServer), war.getRelative(project)));
            }
        } else {
            project.findOne(pathOpt);
            backstage.addAttached(new App(checkStage(stage), pathOpt));
        }
    }

    private Reference checkStage(String nameAndServer) throws IOException {
        List<Reference> found;

        found = globals.servers().list(nameAndServer);
        switch (found.size()) {
            case 0:
                throw new IOException("no such stage: " + nameAndServer);
            case 1:
                return found.get(0);
            default:
                throw new IOException("stage ambiguous: " + stage);
        }

    }
}
