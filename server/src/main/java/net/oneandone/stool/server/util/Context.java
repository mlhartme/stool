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
import net.oneandone.stool.server.kubernetes.ContainerInfo;
import net.oneandone.stool.server.kubernetes.Engine;
import net.oneandone.stool.server.kubernetes.ImageInfo;
import net.oneandone.stool.server.kubernetes.PodInfo;
import net.oneandone.stool.server.kubernetes.Stats;
import net.oneandone.stool.server.stage.Image;
import net.oneandone.stool.server.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Context for info computation. TODO: naming tweaks */
public class Context {
    public final Engine engine;
    private Map<String, ImageInfo> lazyAllImageMap;
    private final Map<String, List<Image>> stageImages;
    private Map<String, PodInfo> lazyAllPodMap;
    private final Map<String, PodInfo> runningPodOpts;
    private final Map<String, Stage.Current> currentOpts;
    private final Map<String, Map<String, String>> urlMaps;

    // CAUTION: key is the container id; with rw
    private final Map<String, JsonObject> containerInspects;

    // CAUTION: key is the container id
    private final Map<String, Stats> containerStats;

    public Context(Engine engine) {
        this.engine = engine;
        this.lazyAllImageMap = null;
        this.stageImages = new HashMap<>();
        this.lazyAllPodMap = null;
        this.runningPodOpts = new HashMap<>();
        this.currentOpts = new HashMap<>();
        this.urlMaps = new HashMap<>();
        this.containerInspects = new HashMap<>();
        this.containerStats = new HashMap<>();
    }

    public Map<String, ImageInfo> allImages() throws IOException {
        if (lazyAllImageMap == null) {
            lazyAllImageMap = engine.imageList();
        }
        return lazyAllImageMap;
    }

    public List<Image> images(Stage stage) throws IOException {
        List<Image> result;

        result = stageImages.get(stage.getName());
        if (result == null) {
            result = stage.images(engine, allImages());
            stageImages.put(stage.getName(), result);
        }
        return result;
    }

    //--

    public Map<String, PodInfo> allPodMap() throws IOException {
        if (lazyAllPodMap == null) {
            lazyAllPodMap = Stage.allPodMap(engine);
        }
        return lazyAllPodMap;
    }

    public PodInfo runningPodOpt(Stage stage) throws IOException {
        PodInfo result;

        result = runningPodOpts.get(stage.getName());
        if (result == null) {
            result = stage.runningPodOpt(allPodMap());
            runningPodOpts.put(stage.getName(), result);
        }
        return result;
    }

    public Stage.Current currentOpt(Stage stage) throws IOException {
        Stage.Current result;

        result = currentOpts.get(stage.getName());
        if (result == null) {
            result = stage.currentOpt(engine, runningPodOpt(stage));
            currentOpts.put(stage.getName(), result);
        }
        return result;
    }

    // TODO: always the same pool assumed
    public Map<String, String> urlMap(Pool pool, Stage stage) throws IOException {
        Map<String, String> result;

        result = urlMaps.get(stage.getName());
        if (result == null) {
            result = stage.urlMap(engine, pool, allPodMap().values());
            urlMaps.put(stage.getName(), result);
        }
        return result;
    }

    /** @return size of the read-write layer, not size of the root file system */
    public int sizeRw(ContainerInfo info) throws IOException {
        JsonObject obj;

        if (info == null) {
            return 0;
        }
        obj = containerInspect(info.id);
        return (int) (obj.get("SizeRw").getAsLong() / (1024 * 1024));
    }

    public JsonObject containerInspect(String containerId) throws IOException {
        JsonObject result;

        result = containerInspects.get(containerId);
        if (result == null) {
            result = engine.containerInspect(containerId, true);
            containerInspects.put(containerId, result);
        }
        return result;
    }

    public Stats containerStats(String containerId) throws IOException {
        Stats result;

        result = containerStats.get(containerId);
        if (result == null) {
            result = engine.containerStats(containerId);
            containerStats.put(containerId, result);
        }
        return result;
    }
}
