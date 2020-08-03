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
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** List of Apps. Represents .backstage */
public class Project {
    public static Project create(FileNode project) throws IOException {
        FileNode projectYaml;
        Project result;

        projectYaml = projectYaml(project);
        projectYaml.getParent().checkNotExists();
        projectYaml.checkNotExists();
        result = new Project(projectYaml);
        return result;
    }

    public static Project lookup(FileNode dir, Configuration configuration) throws IOException {
        FileNode projectYaml;
        Project result;

        while (dir != null) {
            projectYaml = projectYaml(dir);
            if (projectYaml.isFile()) {
                result = new Project(projectYaml);
                result.load(configuration);
                return result;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /**
     * The backstage name is legacy - I keep it because applications have it in their .gitignores.
     * I create a directory to store the actual data to co-exist with Stool 5
     */
    private static FileNode projectYaml(FileNode project) {
        return project.join(".backstage/project.yaml");
    }

    //--

    private final ObjectMapper yaml;
    public final FileNode directory;
    private final FileNode projectYaml;
    private final List<App> apps;

    private Project(FileNode projectYaml) {
        this.yaml = new ObjectMapper(new YAMLFactory());
        this.directory = projectYaml.getParent().getParent();
        this.projectYaml = projectYaml;
        this.apps = new ArrayList<>();
    }

    public void load(Configuration configuration) throws IOException {
        ObjectNode root;
        ObjectNode stages;
        Reference reference;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;
        String typeAndPath;
        int idx;

        try (Reader src = projectYaml.newReader()) {
            root = (ObjectNode) yaml.readTree(src);
        }
        stages = (ObjectNode) root.get("stages");
        apps.clear();
        iter = stages.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            reference = configuration.reference(entry.getKey());
            typeAndPath = entry.getValue().asText();
            idx = typeAndPath.indexOf('@');
            if (idx == -1) {
                throw new IllegalStateException(typeAndPath);
            }
            apps.add(new App(reference, Source.Type.valueOf(typeAndPath.substring(0, idx).toUpperCase()), typeAndPath.substring(idx + 1)));
        }
    }

    public int size() {
        return apps.size();
    }

    public List<App> list() {
        return Collections.unmodifiableList(apps);
    }

    public App lookup(String stage) { // TODO: different clients ...
        for (App app : apps) {
            if (stage.equals(app.reference.stage)) {
                return app;
            }
        }
        return null;
    }

    public App lookup(Reference reference) { // TODO: different clients ...
        for (App app : apps) {
            if (reference.equals(app.reference)) {
                return app;
            }
        }
        return null;
    }

    public void add(Source source, Reference reference) throws IOException {
        add(new App(reference, source.type, source.directory.getRelative(directory)));
    }

    public void add(App app) throws IOException {
        if (lookup(app.reference.stage) != null) {
            throw new IOException("duplicate app: " + app.reference.stage);
        }
        apps.add(app);
    }

    public boolean remove(String stage) {
        for (App app : apps) {
            if (stage.equals(app.reference.stage)) {
                apps.remove(app);
                return true;
            }
        }
        return false;
    }

    public boolean remove(Reference reference) {
        for (App app : apps) {
            if (reference.equals(app.reference)) {
                apps.remove(app);
                return true;
            }
        }
        return false;
    }

    public void save() throws IOException {
        ObjectNode root;
        ObjectNode stages;

        if (apps.isEmpty()) {
            // prune
            projectYaml.deleteFile();
            projectYaml.getParent().deleteDirectory();
        } else {
            root = yaml.createObjectNode();
            stages = yaml.createObjectNode();
            for (App app : apps) {
                stages.put(app.reference.toString(), app.type.toString().toLowerCase() + "@" + app.path);
            }
            root.set("stages", stages);
            projectYaml.getParent().mkdirOpt();
            try (Writer dest = projectYaml.newWriter()) {
                SequenceWriter sw = yaml.writerWithDefaultPrettyPrinter().writeValues(dest);
                sw.write(root);
            }
        }
    }

    //--

    public String getOriginOrUnknown() throws IOException {
        FileNode dir;

        dir = directory;
        do {
            if (dir.join(".svn").isDirectory()) {
                return "svn:" + svnCheckoutUrl(dir);
            }
            if (dir.join(".git").isDirectory()) {
                return "git:" + git(dir, "config", "--get", "remote.origin.url").exec().trim();
            }
            dir = dir.getParent();
        } while (dir != null);
        return "unknown";
    }

    private static String svnCheckoutUrl(FileNode dir) throws Failure {
        Launcher launcher;
        String str;

        // note: svn info has a "--show-item" switch, but it's available since Subversion 1.9 or newer only,
        // and it needs multiple invocations to get multiple fields
        launcher = new Launcher(dir, "svn", "info");
        launcher.env("LC_ALL", "C");
        str = launcher.exec();
        return svnItem(str, "URL") + "@" + svnItem(str, "Revision");
    }

    private static String svnItem(String str, String item) {
        int start;
        int end;

        item = item + ":";
        if (str.startsWith(item)) {
            start = item.length();
        } else {
            item = "\n" + item;
            start = str.indexOf(item);
            if (start < 0) {
                throw new IllegalStateException(str + " " + item);
            }
            start += item.length();
        }
        end = str.indexOf("\n", start);
        if (end < 0) {
            throw new IllegalStateException(str + " " + item);
        }
        return str.substring(start, end).trim();
    }

    private static Launcher git(FileNode cwd, String... args) {
        Launcher launcher;

        launcher = new Launcher(cwd, "git");
        launcher.arg(args);
        return launcher;
    }

    //--

    @Override
    public String toString() {
        return directory.getAbsolute();
    }
}
