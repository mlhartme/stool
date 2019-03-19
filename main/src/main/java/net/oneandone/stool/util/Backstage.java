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
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Backstage {
    public static Backstage create(FileNode project) throws MkdirException {
        return new Backstage(file(project).mkdir());
    }

    public static Backstage get(FileNode dir) throws IOException {
        Backstage result;

        result = Backstage.lookup(dir);
        if (result == null) {
            throw new IOException("not a project: " + dir);
        }
        return result;
    }

    public static Backstage lookup(FileNode dir) {
        FileNode backstage;

        while (dir != null) {
            backstage = file(dir);
            if (backstage.isDirectory()) {
                return new Backstage(backstage);
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static FileNode file(FileNode project) {
        return project.join(".backstage");
    }

    //--

    private final FileNode directory;

    private Backstage(FileNode directory) {
        this.directory = directory;
    }

    public Project project() throws IOException {
        return Project.load(directory.getParent());
    }

    public FileNode stageOpt() throws IOException {
        FileNode map;

        map = map();
        return map.isFile() ? directory.getWorld().file(map.readString().trim()) : null;
    }

    public void attach(FileNode stage) throws IOException {
        map().writeString(stage.getAbsolute());
    }

    private FileNode map() {
        return directory.join("stage");
    }

    public void remove() throws IOException {
        directory.deleteTree();
    }

    public FileNode createContext() throws IOException {
        FileNode result;

        result = directory.join("context");
        result.deleteTreeOpt();
        result.mkdir();
        return result;
    }

    public FileNode imageLog() {
        return directory.join("image.log");
    }
}
