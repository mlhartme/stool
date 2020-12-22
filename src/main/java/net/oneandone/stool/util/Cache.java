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
package net.oneandone.stool.util;

import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.core.Stage;
import net.oneandone.sushi.fs.World;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Context for info computation. */
public class Cache {
    public final Engine engine;
    private final Map<String, Registry> registries;
    private final Map<String, Map<String, PodInfo>> runningPods;

    public Cache(Engine engine) {
        this.engine = engine;
        this.registries = new HashMap<>();
        this.runningPods = new HashMap<>();
    }

    public Registry registry(Stage stage) throws IOException {
        Registry result;

        result = registries.get(stage.getName());
        if (result == null) {
            result = stage.server.createRegistry(World.create() /* TODO */, stage.getImage());
            registries.put(stage.getName(), result);
        }
        return result;
    }

    public Map<String, PodInfo> runningPods(Stage stage) throws IOException {
        Map<String, PodInfo> result;

        result = runningPods.get(stage.getName());
        if (result == null) {
            result = stage.runningPods(engine);
            runningPods.put(stage.getName(), result);
        }
        return result;
    }
}
