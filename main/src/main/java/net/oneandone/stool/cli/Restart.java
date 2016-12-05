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
package net.oneandone.stool.cli;

import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;

public class Restart extends StageCommand {
    private final boolean fitnesse;
    private final boolean debug;
    private final boolean suspend;

    public Restart(Session session, boolean fitnesse, boolean debug, boolean suspend) {
        super(false, session, /* locking done by subcommands */ Mode.NONE, Mode.NONE, Mode.NONE);
        this.fitnesse = fitnesse;
        this.debug = debug;
        this.suspend = suspend;
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        if (stage.state() == Stage.State.UP || stage.state() == Stage.State.WORKING) {
            new Stop(session, false).doRun(stage);
        } else {
            console.info.println("Tomcat is not running - starting a new instance.");
        }

        new Start(session, fitnesse, debug, suspend).doRun(stage);
        if (session.bedroom.contains(stage.getId())) {
            console.info.println("stopped sleeping");
        }
    }
}
