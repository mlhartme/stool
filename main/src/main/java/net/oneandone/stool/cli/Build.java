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
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Session;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class Build extends StageCommand {
    private final boolean noCache;

    public Build(Session session, boolean noCache) {
        super(false, session, Mode.EXCLUSIVE, Mode.EXCLUSIVE);
        this.noCache = noCache;
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        Ports ports;
        Project project;

        project = Project.load(session.projects().project(stage));
        ports = session.pool().allocate(stage, project.selectedWars(stage.config().select), Collections.emptyMap());
        stage.build(project.wars(), console, ports, noCache);
    }
}
