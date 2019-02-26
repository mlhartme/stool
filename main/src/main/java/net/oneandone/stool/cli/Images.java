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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Images extends StageCommand {
    public Images(Session session) {
        super(session, Mode.NONE, Mode.EXCLUSIVE);
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        Map<String, List<Image>> all;
        List<String> apps;
        Map<String, Image> current;
        Engine engine;
        String marker;
        int idx;
        String currentId;

        engine = stage.session.dockerEngine();
        all = stage.images(engine);
        apps = new ArrayList<>(all.keySet());
        Collections.sort(apps);
        current = stage.currentImages();
        for (String app : apps) {
            currentId = current.get(app).id;
            idx = 0;
            for (Image image : all.get(app)) {
                marker = image.id.equals(currentId) ? "==>" : "   ";
                console.info.printf("%s [%d] %s\n", marker, idx, image.id);
                console.info.println("       app:        " + image.app);
                console.info.println("       comment:    " + image.comment);
                console.info.println("       origin:     " + image.origin);
                console.info.println("       created-at: " + image.created);
                console.info.println("       created-by: " + image.createdBy);
                console.info.println("       created-on: " + image.createdOn);
                idx++;
            }
            stage.rotateLogs(console);

        }
    }
}