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

public class Stop extends StageCommand {
    private boolean sleep;

    public Stop(Session session, boolean sleep) {
        super(false, session, Mode.SHARED, Mode.SHARED, Mode.NONE);
        this.sleep = sleep;
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        boolean alreadySleeping;

        stage.modify();
        alreadySleeping = session.bedroom.contains(stage.getId());
        if (alreadySleeping) {
            if (sleep) {
                console.info.println("warning: stage already marked as sleeping");
            } else {
                console.info.println("going from sleeping to stopped.");
            }
        } else {
            doNormal(stage);
        }
    }

    @Override
    public void doFinish(Stage stage) throws Exception {
        if (sleep) {
            if (session.bedroom.contains(stage.getId())) {
                console.info.println("already sleeping");
            } else {
                session.bedroom.add(session.gson, stage.getId());
            }
            console.info.println("state: sleeping");
        } else {
            session.bedroom.remove(session.gson, stage.getId());
            console.info.println("state: down");
        }
    }

    //--

    public void doNormal(Stage stage) throws Exception {
        stage.stop(console);
    }
}
