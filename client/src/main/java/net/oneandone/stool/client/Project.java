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
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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

    public List<App> list(Configuration serverManager) throws IOException {
        Properties p;
        List<App> result;

        p = backstage.readProperties();
        result = new ArrayList<>(p.size());
        for (Map.Entry<Object, Object> entry : p.entrySet()) {
            result.add(new App(serverManager.serverReference((String) entry.getKey()), (String) entry.getValue()));
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


    public void delete() throws IOException {
        backstage.deleteFile();
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

    protected static void addWars(FileNode current, List<FileNode> result) throws IOException {
        List<FileNode> files;
        List<FileNode> wars;

        files = current.list();
        if (!hasPom(files)) {
            return;
        }

        wars = current.find("target/*.war");
        switch (wars.size()) {
            case 0:
                // do nothing
                break;
            case 1:
                result.add(wars.get(0));
                break;
            default:
                throw new IOException(current + ": wars ambiguous: " + wars);
        }
        for (FileNode file : files) {
            if (file.isDirectory()) {
                addWars(file, result);
            }
        }
    }

    private static boolean hasPom(List<FileNode> list) {
        String name;

        for (FileNode file : list) {
            name = file.getName();
            if (name.equals("pom.xml") || name.equals("workspace.xml")) {
                return true;
            }
        }
        return false;
    }

    private List<FileNode> lazyWars;

    public List<FileNode> wars() throws IOException {
        if (lazyWars == null) {
            lazyWars = new ArrayList<>();
            addWars(directory, lazyWars);
        }
        return lazyWars;
    }
}
