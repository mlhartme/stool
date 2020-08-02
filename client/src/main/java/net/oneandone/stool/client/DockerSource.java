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
package net.oneandone.stool.client;

import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** List of Apps. Represents .backstage */
public class DockerSource extends Source {
    public static List<DockerSource> find(FileNode directory) throws IOException {
        List<DockerSource> result;

        result = new ArrayList<>();
        doFind(directory, result);
        return result;
    }

    private static void doFind(FileNode directory, List<DockerSource> result) throws IOException {
        DockerSource docker;

        docker = createOpt(directory);
        if (docker != null) {
            result.add(docker);
        } else {
            for (FileNode child : directory.list()) {
                if (child.isDirectory() && !"target".equals(directory.getName())) {
                    doFind(child, result);
                }
            }
        }
    }

    public static DockerSource createOpt(FileNode directory) {
        return directory.join("Dockerfile").isFile() ? new DockerSource(directory) : null;
    }

    //--

    public DockerSource(FileNode directory) {
        super(Type.DOCKER, directory);
    }

    public String app() {
        return directory.getName();
    }

    public Map<String, String> implicitArguments() {
        return new HashMap<>();
    }

    public FileNode createContext(Globals globals, Map<String, String> arguments) {
        return directory;
    }

    public String toString() {
        return "Docker " + directory.toString();
    }
}
