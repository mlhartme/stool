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

import net.oneandone.stool.client.App;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.Source;
import net.oneandone.stool.client.WarSource;
import net.oneandone.sushi.fs.file.FileNode;

import java.util.List;

public class Attach extends ProjectCommand {
    private final FileNode pathOpt;
    private final String stage;

    public Attach(Globals globals, FileNode pathOpt, String stage) {
        super(globals);

        this.pathOpt = pathOpt;
        this.stage = stage;
    }

    @Override
    public void doRun(FileNode directory) throws Exception {
        Project project;
        List<? extends Source> wars; // maps directories to war files
        String name;

        project = lookupProject(directory);
        if (project == null) {
            project = Project.create(directory);
        }

        wars = WarSource.findWarsAndCheck(pathOpt != null ? pathOpt : directory, stage);
        for (Source war : wars) {
            name = war.subst(stage);
            project.add(new App(reference(name), war.directory.getRelative(directory)));
        }
        project.save();
    }
}
