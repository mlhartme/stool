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
package net.oneandone.stool.util;

import net.oneandone.stool.EnumerationFailed;
import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

// TODO Move into bedroom?
public class StandbyHandler {
    private Session session;

    public static StandbyHandler with(Session session) {
        StandbyHandler standbyHandler = new StandbyHandler();
        standbyHandler.session = session;
        return standbyHandler;
    }
    public void standby() throws IOException {
        String name;
        boolean save;
        Stage stage;
        EnumerationFailed problems;

        save = false;
        problems = new EnumerationFailed();
        for (FileNode wrapper : session.getWrappers()) {
            try {
                stage = Stage.load(session, wrapper);
                name = stage.getName();
                if (stage.state() == Stage.State.UP) {
                    save = true;
                    session.console.info.println("[" + stage.getName() + "]");
                    stage.stop(session.console);
                    session.bedroom.add(name);
                }
            } catch (IOException e) {
                problems.add(wrapper, e);
                // fall-through
            }
        }
        if (save) {
            session.saveConfiguration();
        }
        if (problems.getMessage() != null) {
            throw new IOException(problems);
        }
    }


    public void awake() throws IOException {
        FileNode wrapper;
        boolean save;
        Stage stage;
        EnumerationFailed problems;

        save = false;
        problems = new EnumerationFailed();
        session.console.info.println("waking up " + session.bedroom.stages().size() + " stages");
        for (String name : session.bedroom.stages()) {
            wrapper = session.wrappers.join(name);
            try {
                stage = Stage.load(session, wrapper);
                session.console.info.println("[" + stage.getName() + "]");
                stage.start(session.console, Ports.allocate(stage));
                session.bedroom.remove(name);
                save = true;
            } catch (IOException e) {
                problems.add(wrapper, e);
                // fall through
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
        if (save) {
            session.saveConfiguration();
        }
        if (problems.getMessage() != null) {
            throw new IOException(problems);
        }
    }

}
