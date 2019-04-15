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
import net.oneandone.inline.Console;
import net.oneandone.stool.client.Client;
import net.oneandone.stool.client.Project;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Detach extends ProjectCommand {
    public Detach(Globals globals, FileNode project) {
        super(globals, project);
    }

    @Override
    public void doRun(FileNode project) throws IOException {
        Project backstage;

        backstage = Project.lookup(project);
        if (backstage == null) {
            throw new ArgumentException("project is not attached");
        }
        backstage.removeBackstage();
    }
}
