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

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Option;

import java.io.IOException;

public class Restart extends StageCommand {
    @Option("debug")
    private boolean debug = false;

    public Restart(Session session) throws IOException {
        super(session);
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        if (stage.state() == Stage.State.UP || stage.state() == Stage.State.WORKING) {
            new Stop(session).doInvoke(stage);
        } else {
            console.info.println("Tomcat is not running - starting a new instance.");
        }
        // tomcat may take some time
        Thread.sleep(5000);

        new Start(session, debug).doInvoke(stage);
        if (session.bedroom.stages().contains(stage.getName())) {
            console.info.println("stopped sleeping");
        }
    }
}
