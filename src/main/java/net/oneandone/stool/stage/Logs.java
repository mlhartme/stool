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
package net.oneandone.stool.stage;

import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Logs {
    private final FileNode dir;

    public Logs(FileNode dir) {
        this.dir = dir;
    }

    public List<String> list() throws IOException {
        List<String> files;

        if (!dir.exists()) {
            files = Collections.emptyList();
        } else {
            files = new ArrayList<>();
        }
        for (Node node : dir.find("**/*.log")) {
            files.add(Strings.replace(node.getRelative(dir), "/", "::"));
        }
        return files;
    }

    public String file(String filename) throws IOException {
        FileNode node;
        String file;

        file = Strings.replace(filename, "::", "/");
        file = Strings.replace(file, "..", "");
        node = dir.join(file);
        node.checkFile();

        return node.getAbsolute();
    }
}
