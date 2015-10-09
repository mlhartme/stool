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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SystemStartStop extends StageCommand {
    private final Session session;
    private final boolean start;

    public SystemStartStop(Session session, boolean start) {
        super(session);
        this.session = session;
        this.start = start;
    }

    protected List<Stage> defaultSelected(EnumerationFailed problems) throws IOException {
        List<Stage> result;
        List<Stage> system;

        result = all(problems);

        // put dashboard at the end -- work-around for dashboard problem: sleep state is not updated properly
        system = new ArrayList<>();
        for (Stage stage : result) {
            if (stage.isSystem()) {
                system.add(stage);
            }
        }
        result.removeAll(system);
        result.addAll(system);
        return result;
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        if (start) {
            if (stage.isSystem() || stage.state() == Stage.State.SLEEPING) {
                session.console.info.println("[" + stage.getName() + "]");
                new Start(stage.session, false, false).doInvoke(stage);
            }
        } else {
            if (stage.state() == Stage.State.UP) {
                session.console.info.println("[" + stage.getName() + "]");
                new Stop(stage.session, true).doInvoke(stage);
            }
        }
    }
}
