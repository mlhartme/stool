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

import com.google.gson.JsonObject;
import net.oneandone.stool.kubernetes.DeploymentInfo;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.server.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Context for info computation. TODO: naming tweaks */
public class Context {
    public final Engine engine;
    public final Registry registry;
    private final Map<String, List<TagInfo>> stageImages;
    private final Map<String, PodInfo> runningPodOpts;
    private final Map<String, Stage.Current> currentOpts;
    private final Map<String, Map<String, String>> urlMaps;

    public Context(Engine engine, Registry registry) {
        this.engine = engine;
        this.registry = registry;
        this.stageImages = new HashMap<>();
        this.runningPodOpts = new HashMap<>();
        this.currentOpts = new HashMap<>();
        this.urlMaps = new HashMap<>();
    }

    public DeploymentInfo deploymentOpt(Stage stage) throws IOException {
        return engine.deploymentProbe(stage.deploymentName());
    }

    public List<TagInfo> images(Stage stage) throws IOException {
        List<TagInfo> result;

        result = stageImages.get(stage.getName());
        if (result == null) {
            result = stage.images(registry);
            stageImages.put(stage.getName(), result);
        }
        return result;
    }

    //--

    public PodInfo runningPodOpt(Stage stage) throws IOException {
        PodInfo result;

        result = runningPodOpts.get(stage.getName());
        if (result == null) {
            result = stage.runningPodOpt(engine);
            runningPodOpts.put(stage.getName(), result);
        }
        return result;
    }

    public Stage.Current currentOpt(Stage stage) throws IOException {
        Stage.Current result;

        result = currentOpts.get(stage.getName());
        if (result == null) {
            result = stage.currentOpt(registry, runningPodOpt(stage));
            currentOpts.put(stage.getName(), result);
        }
        return result;
    }

    public Map<String, String> urlMap(Stage stage) throws IOException {
        Map<String, String> result;

        result = urlMaps.get(stage.getName());
        if (result == null) {
            result = stage.urlMap(engine, registry);
            urlMaps.put(stage.getName(), result);
        }
        return result;
    }

    /** @return size of the read-write layer, not size of the root file system */
    public int sizeRw(String containerId) throws IOException {
        JsonObject obj;

        if (containerId == null) {
            return 0;
        }
        return 0;
        /*
        obj = containerInspect(containerId);
        return (int) (obj.get("SizeRw").getAsLong() / (1024 * 1024)); */
    }

}
