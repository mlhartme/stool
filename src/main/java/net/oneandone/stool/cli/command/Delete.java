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
import net.oneandone.stool.cli.Workspace;
import net.oneandone.stool.cli.Reference;

public class Delete extends IteratedStageCommand {
    private final boolean batch;

    public Delete(Globals globals, boolean batch) {
        super(globals);
        this.batch = batch;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        Workspace workspace;

        if (!batch) {
            console.info.println("Ready to delete stage " + reference + "?");
            console.pressReturn();
        }

        reference.client.delete(reference.stage);

        workspace = lookupWorkspace();
        if (workspace != null) {
            if (workspace.remove(reference)) {
                console.info.println("detaching stage: " + reference);
            }
            workspace.save();
        }
    }
}
