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
import net.oneandone.stool.client.Reference;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.List;

public abstract class ProjectCommand extends ClientCommand {
    public ProjectCommand(Globals globals) {
        super(globals);
    }

    @Override
    public void run() throws Exception {
        doRun(working);
    }

    public abstract void doRun(FileNode currentProject) throws Exception;

    protected Reference reference(String nameAndServer) throws IOException {
        List<Reference> found;

        found = globals.configuration().list(nameAndServer);
        switch (found.size()) {
            case 0:
                throw new IOException("no such stage: " + nameAndServer);
            case 1:
                return found.get(0);
            default:
                throw new IOException("stage ambiguous: " + nameAndServer);
        }

    }
}
