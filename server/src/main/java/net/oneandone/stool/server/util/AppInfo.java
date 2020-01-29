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
package net.oneandone.stool.server.util;

import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.stage.Image;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.sushi.util.Separator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppInfo {
    private final Server server;
    private final Engine engine;

    public AppInfo(Server server, Engine engine) {
        this.server = server;
        this.engine = engine;
    }

    public List<String> run(String name) throws Exception {
        Stage stage;
        List<Image> all;
        Stage.Current current;
        String marker;
        List<String> result;
        List<String> args;

        result = new ArrayList<>();
        stage = server.load(name);
        all = stage.images(engine);
        current = stage.currentOpt(engine);
        if (current == null) { // TODO: hack ...
            current = new Stage.Current(all.get(all.size() - 1), null);
        }
        for (Image image : all) {
            marker = image.repositoryTag.equals(current.image.repositoryTag) ? "<==" : "";
            result.add(image.tag + "  " + marker);
            result.add("   comment:     " + image.comment);
            result.add("   origin-scm:  " + image.originScm);
            result.add("   origin-user: " + image.originUser);
            result.add("   created-at:  " + image.createdAt);
            result.add("   created-by:  " + image.createdBy);
            result.add("   memory:      " + image.memory);
            result.add("   disk:        " + image.disk);
            result.add("   build args:");
            args = new ArrayList<>(image.args.keySet());
            Collections.sort(args);
            for (String arg : args) {
                result.add("       " + arg + ": \t" + image.args.get(arg));
            }
            result.add("   secrets:    " + Separator.COMMA.join(image.faultProjects));
        }
        return result;
    }
}
