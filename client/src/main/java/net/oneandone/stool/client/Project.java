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
import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** List of Apps. Represents .backstage */
public class Project {
    public static Project create(FileNode project) throws IOException {
        FileNode backstage;
        Project result;

        backstage = backstage(project);
        backstage.getParent().checkNotExists();
        backstage.checkNotExists();
        result = new Project(backstage);
        return result;
    }

    public static Project lookup(FileNode dir, Configuration configuration) throws IOException {
        FileNode backstage;
        Project result;

        while (dir != null) {
            backstage = backstage(dir);
            if (backstage.isFile()) {
                result = new Project(backstage);
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
    private static FileNode backstage(FileNode project) {
        return project.join(".backstage/project.yaml");
    }

    //--

    private final ObjectMapper yaml;
    private final FileNode directory;
    private final FileNode backstage;
    private final List<App> apps;

    private Project(FileNode backstage) {
        this.yaml = new ObjectMapper(new YAMLFactory());
        this.directory = backstage.getParent();
        this.backstage = backstage;
        this.apps = new ArrayList<>();
    }

    public void load(Configuration configuration) throws IOException {
        ObjectNode root;
        ObjectNode stages;

        String current;
        String context;
        Reference reference;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        try (Reader src = backstage.newReader()) {
            root = (ObjectNode) yaml.readTree(src);
        }
        context = root.get("context").asText();
        current = configuration.defaultContext().name;
        if (!context.equals(current)) {
            throw new IOException("context mismatch: project context is " + context + ", but current context is " + current);
        }
        stages = (ObjectNode) root.get("stages");
        apps.clear();
        iter = stages.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            reference = configuration.reference(entry.getKey());
            apps.add(new App(reference, entry.getValue().asText()));
        }
    }

    public int size() {
        return apps.size();
    }

    public List<App> list() {
        return Collections.unmodifiableList(apps);
    }

    public App lookup(String stage) {
        for (App app : apps) {
            if (stage.equals(app.reference.stage)) {
                return app;
            }
        }
        return null;
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

    public void save() throws IOException {
        ObjectNode root;
        ObjectNode stages;

        if (apps.isEmpty()) {
            // prune
            backstage.deleteFile();
            backstage.getParent().deleteDirectory();
        } else {
            root = yaml.createObjectNode();
            root.put("context", apps.iterator().next().reference.client.getContext());
            stages = yaml.createObjectNode();
            for (App app : apps) {
                stages.put(app.reference.stage, app.path);
            }
            root.set("stages", stages);
            backstage.getParent().mkdirOpt();
            try (Writer dest = backstage.newWriter()) {
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

    //--

    public static final String SUBST = "_";

    public static boolean hasSubst(String name) {
        return name.contains(SUBST);
    }

    public static String subst(String name, FileNode war) throws IOException {
        return name.replace(SUBST, App.app(war));
    }

    public static Map<FileNode, FileNode> findWarsAndCheck(FileNode directory, String stage) throws IOException {
        Map<FileNode, FileNode> wars;

        directory.checkDirectory();
        wars = findWars(directory);
        if (wars.isEmpty()) {
            throw new ArgumentException(directory.getAbsolute() + ": no wars found - did you build your project?");
        } else if (wars.size() > 1) {
            if (!stage.contains(SUBST)) {
                throw new ArgumentException(stage + ": missing '" + SUBST + "' in stage name to attach " + wars.size() + " stages");
            }
        }
        return wars;
    }

    public static Map<FileNode, FileNode> findWars(FileNode directory) throws IOException {
        Map<FileNode, FileNode> result;

        result = new HashMap<>();
        addWars(directory, result);
        return result;
    }

    private static void addWars(FileNode directory, Map<FileNode, FileNode> result) throws IOException {
        FileNode war;

        war = warMatcher(directory);
        if (war != null) {
            if (result.put(directory, war) != null) {
                throw new IllegalStateException(result.toString());
            }
        } else {
            for (FileNode child : directory.list()) {
                if (child.isDirectory()) {
                    addWars(child, result);
                }
            }
        }
    }

    public static FileNode warMatcher(FileNode directory) throws IOException {
        List<FileNode> lst;

        if (!directory.join("pom.xml").isFile()) {
            return null;
        }
        lst = directory.find("target/*.war");
        switch (lst.size()) {
            case 0:
                return null;
            case 1:
                return lst.get(0);
            default:
                throw new IOException("ambiguous: " + directory + " " + lst);
        }
    }
}
