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
package net.oneandone.stool.cli.command;

import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.Reference;
import net.oneandone.stool.cli.Workspace;

import java.io.IOException;
import java.util.List;

public class Attach extends ClientCommand {
    protected final String stage;

    public Attach(Globals globals, String stage) {
        super(globals);
        this.stage = stage;
    }

    @Override
    public void run() throws IOException {
        Workspace workspace;
        Reference reference;

        workspace = lookupWorkspace();
        if (workspace == null) {
            workspace = Workspace.create(world.getWorking());
        }
        reference = resolve(stage);
        try {
            workspace.add(reference);
        } catch (IOException e) {
            throw new IOException("failed to attach stage: " + e.getMessage(), e);
        }
        workspace.save();
    }

    protected Reference resolve(String name) throws IOException {
        List<Reference> found;

        found = globals.configuration().list(name, globals.caller());
        switch (found.size()) {
            case 0:
                throw new IOException("no such stage: " + name);
            case 1:
                return found.get(0);
            default:
                throw new IOException("stage ambiguous: " + name);
        }
    }
}
