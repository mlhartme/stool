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
import net.oneandone.stool.core.Settings;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/** Maps apps to stages. Represents .backstage/workspace.yaml */
public class Workspace {
    public static Workspace load(FileNode file, Settings configuration, Caller caller) throws IOException {
        Workspace result;
        ObjectNode root;
        ArrayNode array;

        result = new Workspace(configuration.yaml, file);
        try (Reader src = file.newReader()) {
            root = (ObjectNode) result.yaml.readTree(src);
        }
        array = (ArrayNode) root.get("stages");
        for (JsonNode node : array) {
            result.stages.add(configuration.reference(node.asText(), configuration, caller));
        }
        return result;
    }

    //--

    private final ObjectMapper yaml;
    private final FileNode workspaceYaml;
    private final List<Reference> stages;

    public Workspace(ObjectMapper yaml, FileNode workspaceYaml) {
        this.yaml = yaml;
        this.workspaceYaml = workspaceYaml;
        this.stages = new ArrayList<>();
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
        return workspaceYaml.getAbsolute();
    }
}
