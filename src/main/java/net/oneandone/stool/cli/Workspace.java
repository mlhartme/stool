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
package net.oneandone.stool.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.stool.core.Configuration;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/** Maps apps to stages. Represents .backstage/workspace.yaml */
public class Workspace {
    public static Workspace create(FileNode directory) throws IOException {
        FileNode workspaceYaml;
        Workspace result;

        workspaceYaml = workspaceYaml(directory);
        workspaceYaml.getParent().checkNotExists();
        workspaceYaml.checkNotExists();
        result = new Workspace(workspaceYaml);
        return result;
    }

    public static Workspace lookup(FileNode dir, Configuration configuration, Caller caller) throws IOException {
        FileNode workspaceYaml;
        Workspace result;

        while (dir != null) {
            workspaceYaml = workspaceYaml(dir);
            if (workspaceYaml.isFile()) {
                result = new Workspace(workspaceYaml);
                result.load(configuration, caller);
                return result;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /**
     * The name backstage is legacy - I keep it because applications have it in their .gitignores.
     * I create a directory to store the actual data to co-exist with Stool 5
     */
    private static FileNode workspaceYaml(FileNode directory) {
        return directory.join(".backstage/workspace.yaml");
    }

    //--

    private final ObjectMapper yaml;
    public final FileNode directory;
    private final FileNode workspaceYaml;
    private final List<Reference> stages;

    private Workspace(FileNode workspaceYaml) {
        this.yaml = new ObjectMapper(new YAMLFactory());
        this.directory = workspaceYaml.getParent().getParent();
        this.workspaceYaml = workspaceYaml;
        this.stages = new ArrayList<>();
    }

    public void load(Configuration configuration, Caller caller) throws IOException {
        ObjectNode root;
        ArrayNode array;

        try (Reader src = workspaceYaml.newReader()) {
            root = (ObjectNode) yaml.readTree(src);
        }
        array = (ArrayNode) root.get("stages");
        stages.clear();
        for (JsonNode node : array) {
            stages.add(configuration.reference(node.asText(), caller));
        }
    }

    public int size() {
        return stages.size();
    }

    public List<Reference> references() {
        return new ArrayList<>(stages);
    }

    public boolean contains(Reference reference) {
        return stages.contains(reference);
    }

    public void add(Reference reference) throws IOException {
        if (contains(reference)) {
            throw new IOException("duplicate stage: " + reference);
        }
        stages.add(reference);
    }

    public boolean remove(Reference reference) {
        return stages.remove(reference);
    }

    public void save() throws IOException {
        ObjectNode root;
        ArrayNode array;

        if (stages.isEmpty()) {
            // prune
            workspaceYaml.deleteFile();
            workspaceYaml.getParent().deleteDirectory();
        } else {
            root = yaml.createObjectNode();
            array = yaml.createArrayNode();
            for (Reference reference : stages) {
                array.add(reference.toString());
            }
            root.set("stages", array);
            workspaceYaml.getParent().mkdirOpt();
            try (Writer dest = workspaceYaml.newWriter()) {
                SequenceWriter sw = yaml.writerWithDefaultPrettyPrinter().writeValues(dest);
                sw.write(root);
            }
        }
    }

    //--

    @Override
    public String toString() {
        return directory.getAbsolute();
    }
}
