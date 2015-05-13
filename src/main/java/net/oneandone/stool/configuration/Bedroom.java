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
package net.oneandone.stool.configuration;

import com.google.gson.Gson;
import net.oneandone.stool.util.Files;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Bedroom {
    public static final String FILENAME = "sleep.json";

    public static Bedroom loadOrCreate(Gson gson, FileNode home) throws IOException {
        return loadOrCreateDir(gson, home.join("conf"));
    }

    public static Bedroom loadOrCreateDir(Gson gson, FileNode dir) throws IOException {
        Bedroom bedroom;
        FileNode file;

        file = dir.join(FILENAME);
        if (file.exists()) {
            bedroom = gson.fromJson(file.readString(), Bedroom.class);
            bedroom.file = file;
        } else {
            bedroom = new Bedroom(file);
        }
        return bedroom;
    }

    //--

    public List<String> sleep;

    private transient FileNode file;

    public Bedroom(FileNode file) {
        this.file = file;
        this.sleep = new ArrayList<>();
    }

    public void add(Gson gson, String stage) throws IOException {
        sleep.add(stage);
        save(gson);
    }

    public void remove(Gson gson, String stage) throws IOException {
        if (sleep.contains(stage)) {
            sleep.remove(stage);
        }
        save(gson);
    }

    public void save(Gson gson) throws IOException {
        Files.stoolFile(file.writeString(gson.toJson(this)));
    }

    public int size() {
        return sleep.size();
    }

    public Collection<String> stages() {
        return Collections.unmodifiableCollection(sleep);
    }

    public FileNode file() {
        return file;
    }
}
