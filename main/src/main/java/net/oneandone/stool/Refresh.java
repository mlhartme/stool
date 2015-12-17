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
import net.oneandone.sushi.cli.Option;

import java.io.IOException;

public class Refresh extends StageCommand {
    @Option("build")
    private boolean build;

    @Option("restore")
    private boolean restore;

    public Refresh(Session session) {
        super(session, Mode.NONE, Mode.SHARED, Mode.SHARED);
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        if (restore) {
            stage.restoreFromBackup(console);
        } else {
            invokeNormal(stage);
        }
    }

    @Override
    public boolean isNoop(Stage stage) throws IOException {
        return !build && stage.owner().equals(session.user) && !stage.refreshPending(console);
    }

    public void invokeNormal(Stage stage) throws Exception {
        console.info.println("refreshing " + stage.getDirectory());
        if (stage.refreshPending(console)) {
            stage.executeRefresh(console);
        }
        if (build) {
            new Build(session).doInvoke(stage);
        }
    }
}
