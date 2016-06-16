/**
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
package net.oneandone.stool;

import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Value;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class OpeningCommand extends SessionCommand {
    public OpeningCommand(Session session) {
        super(session, Mode.NONE);
    }

    @Override
    public void doInvoke() throws Exception {
        if (inShell()) {
            throw new IOException("This command opens a new shell, exit the current shell first.");
        }
        doOpening();
    }

    @Override
    protected String[] extraShellLines() {
        return new String[] { "bash -rcfile " + session.environment.stoolBin(world).join("bash.rc").getAbsolute() };
    }

    public abstract void doOpening() throws Exception;
}
