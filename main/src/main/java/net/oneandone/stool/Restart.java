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

public class Restart extends StageCommand {
    @Option("debug")
    private boolean debug = false;

    @Option("suspend")
    private boolean suspend = false;

    public Restart(Session session) {
        super(session, Mode.NONE, Mode.NONE, Mode.NONE);
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        if (stage.state() == Stage.State.UP || stage.state() == Stage.State.WORKING) {
            new Stop(session).doInvoke(stage);
        } else {
            console.info.println("Tomcat is not running - starting a new instance.");
        }

        new Start(session, debug, suspend).doInvoke(stage);
        if (session.bedroom.stages().contains(stage.getName())) {
            console.info.println("stopped sleeping");
        }
    }
}
