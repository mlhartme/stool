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
package net.oneandone.stool.overview;

import com.google.gson.Gson;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BuildStats {
    public static BuildStats load(FileNode logDir, Stage stage) throws IOException {
        FileNode file;
        BuildStats result;

        file = logDir.join(stage.getName() + ".stats");
        if (file.exists()) {
            result = stage.session.gson.fromJson(file.readString(), BuildStats.class);
        } else {
            result = new BuildStats();
        }
        result.file = file;
        result.gson = stage.session.gson;
        return result;
    }

    private final Map<String, Long> stats;
    private transient Node file;
    private transient Gson gson;

    public BuildStats() {
        this.stats = new HashMap<>();
        this.file = null;
        this.gson = null;
    }

    public long getAvgStart() {
        return get("start");
    }
    public long getAvgStop() {
        return get("stop");
    }
    public long getAvgBuild() {
        return get("build");
    }
    public long getAvgRefresh() {
        return get("refresh");
    }
    public long getAvgRestart() {
        return get("restart");
    }

    private long get(String command) {
        Long result;

        result = stats.get(command);
        return result == null ? 0 : result;
    }

    public void save() throws IOException {
        Files.stoolFile(file.writeString(gson.toJson(this)));
    }

    public void add(String command, long latest) {
        Long time;

        time = stats.get(command);
        if (time == null) {
            time = latest;
        } else {
            time = time + latest;
        }
        stats.put(command, time);
    }
}
