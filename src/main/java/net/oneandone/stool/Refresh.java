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

public class Refresh extends StageCommand {
    @Option("build")
    private boolean build;

    @Option("stop")
    private boolean stop;

    @Option("own")
    private boolean own;

    @Option("debug")
    private boolean debug;

    @Option("suspend")
    private boolean suspend;

    @Option("restore")
    private boolean restore;

    public Refresh(Session session) {
        super(session);
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        if (restore) {
            stage.restoreFromBackup(console);
        } else {
            invokeNormal(stage);
        }
    }

    public void invokeNormal(Stage stage) throws Exception {
        String me;
        boolean stopped;
        String chowned;

        me = session.user;

        // Performance hack -- avoid restart without stanges
        // Note that refreshAvailable needs to be owner, so this tweak is unavailable for not-owned stages
        if (!build && !stop && stage.owner().equals(me) && !stage.refreshPending(console)) {
            console.info.println("nothing to refresh");
            return;
        }


        if (stage.state() == Stage.State.UP) {
            new Stop(session).doInvoke(stage);
            stopped = true;
        } else {
            stopped = false;
        }
        if (stage.owner().equals(me)) {
            chowned = null;
        } else {
            chowned = stage.owner();
            session.chown(stage, me);
        }

        console.info.println("refreshing " + stage.getDirectory());
        if (stage.refreshPending(console)) {
            stage.executeRefresh(console);
        }
        if (build) {
            new Build(session).doInvoke(stage);
        }
        if (chowned != null) {
            if (own) {
                console.info.println("stage is *not* chowned back to " + chowned);
            } else {
                session.chown(stage, chowned);
            }
        }
        if (stopped) {
            if (stop) {
                console.info.println("stage is *not* re-started");
            } else {
                new Start(session, debug, suspend).doInvoke(stage);
            }
        }
    }
}
