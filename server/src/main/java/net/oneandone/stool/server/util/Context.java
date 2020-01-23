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

import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.stage.Image;
import net.oneandone.stool.server.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Context for info computation */
public class Context {
    public final Engine engine;

    public Context(Engine engine) {
        this.engine = engine;
    }

    private Map<String, Map<String, List<Image>>> stageImages = new HashMap<>();

    public Map<String, List<Image>> images(Stage stage) throws IOException {
        Map<String, List<Image>> result;

        result = stageImages.get(stage.getName());
        if (result == null) {
            result = stage.images(engine);
            stageImages.put(stage.getName(), result);
        }
        return result;
    }
}
