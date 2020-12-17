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

import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.server.Stage;
import net.oneandone.sushi.fs.World;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Context for info computation. */
public class Context {
    public final Engine engine;
    private final Map<String, Registry> registries;
    private final Map<String, List<TagInfo>> stageImages;
    private final Map<String, Map<String, PodInfo>> runningPods;
    private final Map<String, TagInfo> currentOpts;
    private final Map<String, Map<String, String>> urlMaps;

    public Context(Engine engine) {
        this.engine = engine;
        this.registries = new HashMap<>();
        this.stageImages = new HashMap<>();
        this.runningPods = new HashMap<>();
        this.currentOpts = new HashMap<>();
        this.urlMaps = new HashMap<>();
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

    public List<TagInfo> images(Stage stage) throws IOException {
        List<TagInfo> result;

        result = stageImages.get(stage.getName());
        if (result == null) {
            result = stage.images(registry(stage));
            stageImages.put(stage.getName(), result);
        }
        return result;
    }

    //--

    public Map<String, PodInfo> runningPods(Stage stage) throws IOException {
        Map<String, PodInfo> result;

        result = runningPods.get(stage.getName());
        if (result == null) {
            result = stage.runningPods(engine);
            runningPods.put(stage.getName(), result);
        }
        return result;
    }

    public TagInfo tagInfo(Stage stage) throws IOException {
        TagInfo result;

        result = currentOpts.get(stage.getName());
        if (result == null) {
            result = stage.tagInfo(registry(stage));
            currentOpts.put(stage.getName(), result);
        }
        return result;
    }

    public Map<String, String> urlMap(Stage stage, Registry registry) throws IOException {
        Map<String, String> result;

        result = urlMaps.get(stage.getName());
        if (result == null) {
            result = stage.urlMap(registry);
            urlMaps.put(stage.getName(), result);
        }
        return result;
    }
}