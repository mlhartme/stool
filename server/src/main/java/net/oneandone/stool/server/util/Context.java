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
import net.oneandone.stool.server.docker.ContainerInfo;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.docker.ImageInfo;
import net.oneandone.stool.server.stage.Image;
import net.oneandone.stool.server.stage.Stage;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Context for info computation. TODO: naming tweaks */
public class Context {
    public final Engine engine;
    private Map<String, ImageInfo> lazyAllImageMap;
    private final Map<String, List<Image>> stageImages;
    private Map<String, ContainerInfo> lazyAllContainerMap;
    private final Map<String, ContainerInfo> runningContainerOpts;
    private final Map<String, Stage.Current> currentOpts;
    private final Map<String, Map<String, String>> urlMaps;

    // CAUTION: key is the container id; with rw
    private final Map<String, JsonObject> containerInspects;

    public Context(Engine engine) {
        this.engine = engine;
        this.lazyAllImageMap = null;
        this.stageImages = new HashMap<>();
        this.lazyAllContainerMap = null;
        this.runningContainerOpts = new HashMap<>();
        this.currentOpts = new HashMap<>();
        this.urlMaps = new HashMap<>();
        this.containerInspects = new HashMap<>();
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

    public Map<String, ContainerInfo> allContainerMap() throws IOException {
        if (lazyAllContainerMap == null) {
            lazyAllContainerMap = Stage.allContainerMap(engine);
        }
        return lazyAllContainerMap;
    }

    public ContainerInfo runningContainerOpt(Stage stage) throws IOException {
        ContainerInfo result;

        result = runningContainerOpts.get(stage.getName());
        if (result == null) {
            result = stage.runningContainerOpt(allContainerMap());
            runningContainerOpts.put(stage.getName(), result);
        }
        return result;
    }

    public Stage.Current currentOpt(Stage stage) throws IOException {
        Stage.Current result;

        result = currentOpts.get(stage.getName());
        if (result == null) {
            result = stage.currentOpt(engine, runningContainerOpt(stage));
            currentOpts.put(stage.getName(), result);
        }
        return result;
    }

    // TODO: always the same pool assumed
    public Map<String, String> urlMap(Pool pool, Stage stage) throws IOException {
        Map<String, String> result;

        result = urlMaps.get(stage.getName());
        if (result == null) {
            result = stage.urlMap(engine, pool, allContainerMap().values());
            urlMaps.put(stage.getName(), result);
        }
        return result;
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

    // TODO: move to Engine whould harm performance ...
    // https://github.com/moby/moby/pull/15010
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.n'Z'");

    public long containerStartedAt(String containerId) throws IOException {
        JsonObject state;
        String str;
        LocalDateTime result;

        state = containerInspect(containerId).get("State").getAsJsonObject();
        str = state.get("StartedAt").getAsString();
        try {
            result = LocalDateTime.parse(str, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IOException("cannot parse date: " + str);
        }
        // CAUTION: container executes in GMT timezone
        return result.atZone(ZoneId.of("GMT")).toInstant().toEpochMilli();
    }
}
