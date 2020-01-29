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

import net.oneandone.stool.server.docker.ContainerInfo;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.docker.ImageInfo;
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
    private Map<String, ContainerInfo> lazyAllContainerMap;
    private final Map<String, ContainerInfo> runningContainerOpts;
    private final Map<String, Stage.Current> currentOpts;
    private final Map<String, Map<String, String>> urlMaps;

    public Context(Engine engine) {
        this.engine = engine;
        this.lazyAllImageMap = null;
        this.stageImages = new HashMap<>();
        this.lazyAllContainerMap = null;
        this.runningContainerOpts = new HashMap<>();
        this.currentOpts = new HashMap<>();
        this.urlMaps = new HashMap<>();
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
}
