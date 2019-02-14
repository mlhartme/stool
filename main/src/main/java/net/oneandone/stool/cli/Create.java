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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Project;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Create extends SessionCommand {
    private final String nameTemplate;
    private final FileNode project;

    public Create(Session session, String nameTemplate, FileNode project) {
        super(session, Mode.EXCLUSIVE);
        this.nameTemplate = nameTemplate;
        this.project = project;
    }

    @Override
    public void doRun() throws IOException {
        String origin;
        Project result;
        Stage stage;
        FileNode backstage;

        project.checkDirectory();
        backstage = Project.backstageDirectory(project);
        backstage.checkNotExists();

        origin = Project.origin(project);
        if (origin == null) {
            throw new ArgumentException("unknown scm: " + project);
        }

        result = new Project(origin, project);
        stage = new Stage(session, session.nextStageId(), Project.backstageDirectory(project), session.createStageConfiguration(origin));

        backstage.mkdir();
        stage.config().name = name(project);
        stage.tuneConfiguration(result.size());
        stage.initialize();
        stage.modify();

        session.add(stage.directory, stage.getId());
        session.logging.setStage(stage.getId(), stage.getName());
        console.info.println("stage create: " + stage.getName());
    }

    private String name(FileNode directory) {
        return nameTemplate.replace("%d", directory.getName());
    }
}
