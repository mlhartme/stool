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
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Workspace;
import net.oneandone.stool.client.Reference;

import java.io.IOException;

public abstract class WorkspaceAdd extends ClientCommand {
    private final boolean detached;
    private final String stage;

    public WorkspaceAdd(Globals globals, boolean detached, String stage) {
        super(globals);

        this.detached = detached;
        this.stage = stage;
    }

    @Override
    public void run() throws IOException {
        Workspace workspaceOpt;

        workspaceOpt = lookupWorkspace();
        if (workspaceOpt != null) { // TODO: feels weired
            throw new ArgumentException("workspace already has a stage; detach it first");
        }
        if (detached) {
            workspaceOpt = null;
        } else {
            workspaceOpt = Workspace.create(world.getWorking());
        }
        try {
            add(workspaceOpt, stage);
            if (workspaceOpt != null) {
                workspaceOpt.save();
            }
        } catch (IOException e) {
            try {
                if (workspaceOpt != null) {
                    workspaceOpt.save();
                }
            } catch (IOException e2) {
                e.addSuppressed(e2);
            }
            throw e;
        }
    }

    private void add(Workspace workspaceOpt, String name) throws IOException {
        Reference reference;

        reference = stage(name);
        if (workspaceOpt != null) {
            try {
                workspaceOpt.add(reference);
            } catch (IOException e) {
                throw new IOException("failed to attach stage: " + e.getMessage(), e);
            }
        } else {
            // -detached
        }
    }

    protected abstract Reference stage(String name) throws IOException;
}
