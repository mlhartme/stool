/**
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.stream.MalformedJsonException;
import net.oneandone.sushi.fs.Node;

import java.io.IOException;

public class BuildStats {


    @Expose
    private long start;
    @Expose
    private long stop;
    @Expose
    private long build;
    @Expose
    private long refresh;
    private Node file;

    public BuildStats(Node file) {
        this.file = file;
    }
    public static BuildStats load(Node file) throws IOException {
        BuildStats result;

        result = gson().fromJson(file.readString(), BuildStats.class);
        result.file = file;
        return result;
    }

    public static Gson gson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.excludeFieldsWithoutExposeAnnotation();
        return gsonBuilder.create();
    }

    public long getAvgStart() {
        return start;
    }
    public long getAvgStop() {
        return stop;
    }
    public long getAvgBuild() {
        return build;
    }
    public long getAvgRefresh() {
        return refresh;
    }
    public long getAvgRestart() {
        return start + stop;
    }
    public void start(long startTime) {
        if (start == 0) {
            start = startTime;
        } else {
            start = (start + startTime) / 2;
        }
    }
    public void stop(long stopTime) {
        if (stop == 0) {
            stop = stopTime;
        } else {
            stop = (stop + stopTime) / 2;
        }
    }
    public void build(long buildTime) {
        if (build == 0) {
            build = buildTime;
        } else {
            build = (build + buildTime) / 2;
        }
    }
    public void refresh(long refreshTime) {
        if (refresh == 0) {
            refresh = refreshTime;
        } else {
            refresh = (refresh + refreshTime) / 2;
        }
    }
    public void save() throws IOException {
        Files.stoolFile(file.writeString(gson().toJson(this)));
    }

}
