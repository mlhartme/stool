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

import com.google.gson.JsonObject;
import net.oneandone.stool.docker.Engine;
import net.oneandone.stool.docker.Stats;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Image;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.Session;

import java.io.IOException;
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
        Map<String, Stage.Current> currentMap;
        Engine engine;
        String marker;
        int idx;
        Stage.Current current;

        engine = stage.session.dockerEngine();
        all = stage.images(engine);
        apps = new ArrayList<>(all.keySet());
        Collections.sort(apps);
        currentMap = stage.currentMap();
        for (Map.Entry<String, List<Image>> entry : all.entrySet()) {
            if (!currentMap.containsKey(entry.getKey())) {
                currentMap.put(entry.getKey(), new Stage.Current(entry.getValue().get(0), null));
            }
        }
        for (String app : apps) {
            current = currentMap.get(app);
            idx = 0;
            console.info.println("cpu:       " + cpu(current));
            console.info.println("mem:       " + mem(current));
            console.info.println("container: " + current.container);
            console.info.println("origin:    " + current.image.origin);
            console.info.println("uptime:    " + uptime(current));
            console.info.println("disk-used: " + diskUsed(current));
            for (Image image : all.get(app)) {
                marker = image.id.equals(current.image.id) ? "==>" : "   ";
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

    public int diskUsed(Stage.Current current) throws IOException {
        String container;
        JsonObject obj;

        container = current.container;
        if (container == null) {
            return 0;
        }
        obj = session.dockerEngine().containerInspect(container, true);
        // not SizeRootFs, that's the image size plus the rw layer
        return (int) (obj.get("SizeRw").getAsLong() / (1024 * 1024));
    }

    private String uptime(Stage.Current current) throws IOException {
        String container;

        container = current.container;
        return container == null ? null : Stage.timespan(session.dockerEngine().containerStartedAt(container));
    }

    private Integer cpu(Stage.Current current) throws IOException {
        Engine engine;
        Stats stats;
        String container;

        container = current.container;
        if (container == null) {
            return null;
        }
        engine = session.dockerEngine();
        stats = engine.containerStats(container);
        if (stats != null) {
            return stats.cpu;
        } else {
            // not started
            return 0;
        }
    }

    private Long mem(Stage.Current current) throws IOException {
        String container;
        Engine engine;
        Stats stats;

        container = current.container;
        if (container == null) {
            return null;
        }
        engine = session.dockerEngine();
        stats = engine.containerStats(container);
        if (stats != null) {
            return stats.memoryUsage * 100 / stats.memoryLimit;
        } else {
            // not started
            return 0L;
        }
    }
}
