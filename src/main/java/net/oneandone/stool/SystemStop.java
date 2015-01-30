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
import net.oneandone.stool.util.StandbyHandler;
import net.oneandone.sushi.cli.Option;

import java.io.IOException;

public class SystemStop extends SessionCommand {
    @Option("sleep")
    private boolean sleep;

    public SystemStop(Session session) {
        super(session);
    }

    @Override
    public void doInvoke() throws Exception {
        overview();
        if (sleep) {
            sleep();
        }
    }

    private void sleep() throws IOException {
        StandbyHandler standby;
        standby = StandbyHandler.with(session);
        standby.standby();
    }

    private void overview() throws IOException {
        Overview overview;
        overview = Overview.initiate(session);
        if (overview.stage().state().equals(Stage.State.UP)) {
            overview.stop();
        } else if (overview.stage().state().equals(Stage.State.CRASHED)) {
            session.console.info.println("Overview is crashed.");
        }
    }

}
