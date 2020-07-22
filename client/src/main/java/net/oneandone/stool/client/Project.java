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

import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** List of Apps */
public class Project {
    public static Project create(FileNode project) throws IOException {
        FileNode backstage;
        Project result;

        backstage = backstage(project);
        backstage.checkNotExists();
        backstage.writeProperties(new Properties());
        result = new Project(backstage);
        return result;
    }

    public static Project get(FileNode dir) throws IOException {
        Project result;

        result = Project.lookup(dir);
        if (result == null) {
            throw new IOException("not a project: " + dir);
        }
        return result;
    }

    public static Project lookup(FileNode dir) {
        FileNode backstage;

        while (dir != null) {
            backstage = backstage(dir);
            if (backstage.isFile()) {
                return new Project(backstage);
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static FileNode backstage(FileNode project) {
        return project.join(".backstage");
    }

    //--

    private final FileNode directory;
    private final FileNode backstage;

    private Project(FileNode backstage) {
        this.directory = backstage.getParent();
        this.backstage = backstage;
    }

    public int size() throws IOException {
        return backstage.readProperties().size();
    }

    public List<App> list(Configuration configuration) throws IOException {
        Properties p;
        List<App> result;

        p = backstage.readProperties();
        result = new ArrayList<>(p.size());
        for (Map.Entry<Object, Object> entry : p.entrySet()) {
            result.add(new App(configuration.serverReference((String) entry.getKey()), (String) entry.getValue()));
        }
        return result;
    }

    public void add(App app) throws IOException {
        Properties p;

        p = backstage.readProperties();
        if (p.put(app.reference.toString(), app.path) != null) {
            throw new IOException("duplicate stage: " + app.reference);
        }
        backstage.writeProperties(p);
    }

    public boolean remove(Reference reference) throws IOException {
        Properties p;

        p = backstage.readProperties();
        if (p.remove(reference.toString()) == null) {
            return false;
        }
        backstage.writeProperties(p);
        return true;
    }

    public void prune() throws IOException {
        if (backstage.readProperties().isEmpty()) {
            backstage.deleteFile();
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
