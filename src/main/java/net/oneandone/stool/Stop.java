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

public class Stop extends StageCommand {
    @Option("sleep")
    private boolean sleep;

    public Stop(Session session) throws IOException {
        super(session);
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        timeStart();
        invokeNormal(stage);
        stage.buildStats().stop(executionTime());
        stage.buildStats().save(session.gson);

    }

    public void invokeNormal(Stage stage) throws Exception {
        boolean alreadySleeping;
        String name;

        name = stage.getName();
        alreadySleeping = session.bedroom.stages().contains(name);
        if (alreadySleeping) {
            if (sleep) {
                console.info.println("warning: stage already marked as sleeping");
            } else {
                console.info.println("going from sleeping to stopped.");
            }
        } else {
            stage.stop(console);
        }
        if (sleep) {
            if (!alreadySleeping) {
                session.bedroom.add(name);
            }
            console.info.println("state: sleeping");
        } else {
            session.bedroom.remove(name);
            console.info.println("state: down");
        }
    }
}
