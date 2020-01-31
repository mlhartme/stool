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
import net.oneandone.sushi.fs.file.FileNode;

import java.util.List;

public class Attach extends ProjectCommand {
    private final String stage;
    private final String pathOpt;

    public Attach(Globals globals, String stage) {
        super(globals);

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
    public void doRun(FileNode directory) throws Exception {
        Project project;
        List<FileNode> wars;
        String nameAndServer;

        project = Project.lookup(directory);
        if (project == null) {
            project = Project.create(directory);
        }
        if (pathOpt == null) {
            wars = project.wars();
            if (wars.isEmpty()) {
                throw new ArgumentException("no wars found - did you build your project?");
            }
            for (FileNode war : wars) {
                nameAndServer = App.app(war) + "." + stage;
                project.add(new App(reference(nameAndServer), war.getRelative(directory)));
            }
        } else {
            directory.findOne(pathOpt);
            project.add(new App(reference(stage), pathOpt));
        }
    }
}
