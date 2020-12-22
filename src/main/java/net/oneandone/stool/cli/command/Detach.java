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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.Workspace;
import net.oneandone.stool.cli.Reference;

import java.io.IOException;

public class Detach extends IteratedStageCommand {
    public Detach(Globals globals) {
        super(globals);
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        Workspace workspace;

        workspace = lookupWorkspace();
        if (workspace == null) {
            throw new ArgumentException("no workspace to detach from");
        }
        if (!workspace.remove(reference)) {
            throw new IOException("stage is not attached: " + reference);
        }
        workspace.save();
    }
}
