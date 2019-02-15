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
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class Create extends SessionCommand {
    private final FileNode project;
    private final Map<String, String> config;

    public Create(Session session, FileNode project) {
        super(session, Mode.EXCLUSIVE);
        this.project = project;
        this.config = new LinkedHashMap<>();
    }

    public void property(String str) {
        int idx;
        String key;
        String value;

        idx = str.indexOf('=');
        if (idx == -1) {
            throw new ArgumentException("Invalid configuration argument. Expected <key>=<value>, got " + str);
        }
        key = str.substring(0, idx);
        value = str.substring(idx + 1);
        if (config.put(key, value) != null) {
            throw new ArgumentException("already configured: " + key);
        }
    }

    @Override
    public void doRun() throws IOException {
        String origin;
        Stage stage;
        FileNode backstage;
        Property property;

        project.checkDirectory();
        backstage = Project.backstageDirectory(project);
        backstage.checkNotExists();

        origin = Project.origin(project);
        if (origin == null) {
            throw new ArgumentException("unknown scm: " + project);
        }
        stage = new Stage(session, session.nextStageId(), Project.backstageDirectory(project), session.createStageConfiguration(origin));
        backstage.mkdir();
        stage.config().name = project.getName();
        stage.config().tuneMemory(stage.session.configuration.baseMemory, new Project(origin, project).size());
        for (Map.Entry<String, String> entry : config.entrySet()) {
            property = stage.propertyOpt(entry.getKey());
            if (property == null) {
                throw new ArgumentException("unknown property: " + entry.getKey());
            }
            property.set(entry.getValue());
        }
        Project.checkName(stage.config().name);
        stage.saveConfig();
        stage.modify();

        session.add(stage.directory, stage.getId());
        session.logging.setStage(stage.getId(), stage.getName());
        console.info.println("stage create: " + stage.getName());
    }

}
