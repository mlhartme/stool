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
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Select extends SessionCommand {
    private String stageName;

    public Select(Session session) {
        super(session, Mode.NONE);
    }

    @Remaining
    public Select stageToSelect(String stage) {
        stageName = stage;
        return this;
    }

    @Override
    public void doInvoke() throws Exception {
        String msg;
        boolean none;

        if (stageName == null) {
            msg = "";
        } else {
            none = "none".equals(stageName);
            if (inShell()) {
                msg = none ? "Try 'exit' instead." : "Use 'exit' and 'stool open " + stageName + "' instead.";
            } else {
                msg = none ? "" : "Try 'stool open " + stageName + "' instead.";
            }
        }

        throw new ArgumentException("This command is no longer supported.\n" + msg);
    }
}
