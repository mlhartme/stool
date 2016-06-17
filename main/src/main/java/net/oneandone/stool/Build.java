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
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;

public class Build extends StageCommand {
    public Build(Session session) {
        super(session, Mode.NONE, Mode.SHARED, Mode.EXCLUSIVE);
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        String command;
        Launcher launcher;

        stage.checkOwnership();
        stage.checkNotUp();
        command = stage.getBuild();
        console.info.println("[" + stage.getDirectory() + "] " + command);
        launcher = stage.launcher();
        launcher.args(Separator.SPACE.split(command));
        launcher.exec(console.info);
    }
}
