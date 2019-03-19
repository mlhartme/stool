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

import net.oneandone.stool.stage.Project;
import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Projects {
    private final FileNode file;
    /** maps projects to stages */
    private final Map<FileNode, FileNode> map;

    public Projects(FileNode file) {
        this.file = file;
        this.map = new HashMap<>();
    }

    public void load() throws IOException {
        List<String> lines;
        int idx;

        map.clear();
        lines = file.exists() ? file.readLines() : Collections.emptyList();
        for (String line : lines) {
            idx = line.indexOf("=");
            if (idx == -1) {
                throw new IOException("projects file broken: " + idx);
            }
            map.put(file.getWorld().file(line.substring(0, idx)), file.getWorld().file(line.substring(idx + 1)));
        }
    }

    public FileNode stage(FileNode project) throws IOException {
        FileNode result;

        result = stageOpt(project);
        if (result == null) {
            throw new IOException("no stage attached to project " + project);
        }
        return result;
    }

    public FileNode stageOpt(FileNode project) {
        return map.get(project);
    }

    public void remove(Stage stage) throws IOException {
        Iterator<Map.Entry<FileNode, FileNode>> iter;
        FileNode dir;

        iter = map.entrySet().iterator();
        dir = stage.directory;
        while (iter.hasNext()) {
            if (iter.next().getValue().equals(dir)) {
                iter.remove();
            }
        }
        save();
    }

    public boolean remove(Project project) throws IOException {
        if (map.remove(project.getDirectory()) != null) {
            save();
            return true;
        } else {
            return false;
        }
    }

    public boolean hasProject(FileNode project) {
        return map.containsKey(project);
    }

    public void add(FileNode project, FileNode stage) throws IOException {
        map.put(project, stage);
        save();
    }

    public void save() throws IOException {
        List<String> lines;

        lines = new ArrayList<>(map.size());
        for (Map.Entry<FileNode, FileNode> entry : map.entrySet()) {
            lines.add(entry.getKey().getAbsolute() + "=" + entry.getValue().getAbsolute());
        }
        file.writeLines(lines);
    }

}
