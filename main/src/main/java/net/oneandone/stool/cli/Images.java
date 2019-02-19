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

import net.oneandone.stool.docker.Engine;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Image;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;

public class Images extends StageCommand {
    public Images(Session session) {
        super(false, session, Mode.NONE, Mode.EXCLUSIVE);
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        Engine engine;
        int idx;

        engine = stage.session.dockerEngine();
        idx = 0;
        for (Image image : stage.images(engine)) {
            console.info.printf("[%d] %s\n", idx, image.id);
            console.info.println("  comment:    " + image.comment);
            console.info.println("  origin:     " + image.origin);
            console.info.println("  created-at: " + image.created);
            console.info.println("  created-by: " + image.createdBy);
            console.info.println("  created-on: " + image.createdOn);
            idx++;
        }
        stage.modify();
        stage.rotateLogs(console);
    }
}
