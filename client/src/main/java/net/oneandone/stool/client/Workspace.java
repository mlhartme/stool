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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** List of Apps. Represents .backstage */
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

    public static Workspace lookup(FileNode dir, Configuration configuration) throws IOException {
        FileNode workspaceYaml;
        Workspace result;

        while (dir != null) {
            workspaceYaml = workspaceYaml(dir);
            if (workspaceYaml.isFile()) {
                result = new Workspace(workspaceYaml);
                result.load(configuration);
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
    private final Map<App, Reference> map;

    private Workspace(FileNode workspaceYaml) {
        this.yaml = new ObjectMapper(new YAMLFactory());
        this.directory = workspaceYaml.getParent().getParent();
        this.workspaceYaml = workspaceYaml;
        this.map = new LinkedHashMap<>();
    }

    public void load(Configuration configuration) throws IOException {
        ObjectNode root;
        ObjectNode stages;
        Reference reference;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;
        String typeAndPath;
        int idx;

        try (Reader src = workspaceYaml.newReader()) {
            root = (ObjectNode) yaml.readTree(src);
        }
        stages = (ObjectNode) root.get("stages");
        map.clear();
        iter = stages.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            reference = configuration.reference(entry.getKey());
            typeAndPath = entry.getValue().asText();
            idx = typeAndPath.indexOf('@');
            if (idx == -1) {
                throw new IllegalStateException(typeAndPath);
            }
            map.put(new App(Source.Type.valueOf(typeAndPath.substring(0, idx).toUpperCase()), typeAndPath.substring(idx + 1)),
                    reference);
        }
    }

    public int size() {
        return map.size();
    }

    public List<Reference> references() {
        return new ArrayList<>(map.values());
    }

    public App lookup(Reference reference) {
        for (Map.Entry<App, Reference> entry : map.entrySet()) {
            if (reference.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void add(Source source, Reference reference) throws IOException {
        add(new App(source.type, source.directory.getRelative(directory)), reference);
    }

    public void add(App app, Reference reference) throws IOException {
        if (lookup(reference) != null) {
            throw new IOException("duplicate app: " + reference);
        }
        map.put(app, reference);
    }

    public boolean remove(String stage) {
        for (Map.Entry<App, Reference> entry : map.entrySet()) {
            if (stage.equals(entry.getValue())) {
                map.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }

    public boolean remove(Reference reference) {
        for (Map.Entry<App, Reference> entry : map.entrySet()) {
            if (reference.equals(entry.getValue())) {
                map.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }

    public void save() throws IOException {
        ObjectNode root;
        ObjectNode stages;

        if (map.isEmpty()) {
            // prune
            workspaceYaml.deleteFile();
            workspaceYaml.getParent().deleteDirectory();
        } else {
            root = yaml.createObjectNode();
            stages = yaml.createObjectNode();
            for (Map.Entry<App, Reference> entry : map.entrySet()) {
                stages.put(entry.getValue().toString(), entry.getKey().type.toString().toLowerCase() + "@" + entry.getKey().path);
            }
            root.set("stages", stages);
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
