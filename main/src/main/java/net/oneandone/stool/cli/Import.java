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
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Project;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Import extends SessionCommand {
    private final String nameTemplate;
    private final FileNode project;

    public Import(Session session, String nameTemplate, FileNode project) {
        super(session, Mode.EXCLUSIVE);
        this.nameTemplate = nameTemplate;
        this.project = project;
    }

    @Override
    public void doRun() throws IOException {
        String url;
        Project result;

        project.checkDirectory();
        url = Project.origin(project);
        if (url == null) {
            throw new ArgumentException("unknown scm: " + project);
        }
        result = Project.load(session, session.nextStageId(), session.createStageConfiguration(url), project);
        console.info.println("Importing " + result.getDirectory());
        doImport(result, null);
    }

    private void doImport(Project project, String forceName) throws IOException {
        FileNode directory;
        FileNode backstage;
        String name;

        directory = project.getDirectory();
        name = forceName != null ? forceName : name(directory);
        backstage = Project.backstageDirectory(directory);
        if (backstage.exists()) {
            console.info.println("re-using " + backstage);
        } else {
            backstage.mkdir();
            project.getStage().config().name = name;
            project.tuneConfiguration();
            project.initialize();
        }
        project.stage.modify();
        session.add(project.getStage().directory, project.getStage().getId());
        session.logging.setStage(project.getStage().getId(), project.getStage().getName());
        console.info.println("stage imported: " + project.getStage().getName());
    }

    private String name(FileNode directory) {
        return nameTemplate.replace("%d", directory.getName());
    }
}
