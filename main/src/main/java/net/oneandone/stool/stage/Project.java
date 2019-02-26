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
package net.oneandone.stool.stage;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.util.Field;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Project {
    public static Project load(FileNode project) throws IOException {
        return new Project(origin(project), project);
    }

    public static String origin(FileNode dir) throws IOException {
        if (dir.join(".svn").isDirectory()) {
            return "svn:" + svnCheckoutUrl(dir);
        }
        if (dir.join(".git").isDirectory()) {
            return "git:" + git(dir, "config", "--get", "remote.origin.url").exec().trim();
        }
        throw new IOException("not a checkout: " + dir);
    }

    private static String svnCheckoutUrl(FileNode dir) throws Failure {
        Launcher launcher;
        String str;
        int idx;

        launcher = new Launcher(dir, "svn", "info");
        launcher.env("LC_ALL", "C");
        str = launcher.exec();
        idx = str.indexOf("URL:") + 4;
        return str.substring(idx, str.indexOf("\n", idx)).trim();
    }

    private static Launcher git(FileNode cwd, String... args) {
        Launcher launcher;

        launcher = new Launcher(cwd, "git");
        launcher.arg(args);
        return launcher;
    }

    //--

    private final String origin;
    public final FileNode directory;

    public Project(String origin, FileNode directory) {
        this.origin = origin;
        this.directory = directory;
    }

    public FileNode getDirectory() {
        return directory;
    }

    public String getOrigin() {
        return origin;
    }

    //--

    public Map<String, FileNode> selectedWars(List<String> selected) throws IOException {
        Map<String, FileNode> wars;
        Iterator<Map.Entry<String, FileNode>> iter;
        String name;

        wars = new LinkedHashMap<>(wars());
        if (!selected.isEmpty()) {
            iter = wars.entrySet().iterator();
            while (iter.hasNext()) {
                name = iter.next().getKey();
                if (!selected.contains(name)) {
                    iter.remove();
                }
            }
        }
        return wars;
    }

    /** @return nummer of applications */
    public int size() throws IOException {
        return wars().size();
    }


    public List<String> faultProjects() throws IOException {
        List<String> result;

        result = new ArrayList<>();
        for (FileNode war : wars().values()) {
            result.add("file:" + war.getAbsolute());
        }
        return result;
    }

    //--

    @Override
    public String toString() {
        return origin;
    }

    //--

    protected void addWars(FileNode directory, Map<String, FileNode> result) throws IOException {
        List<FileNode> files;
        List<FileNode> wars;

        files = directory.list();
        if (!hasPom(files)) {
            return;
        }

        wars = directory.find("target/*.war");
        switch (wars.size()) {
            case 0:
                // do nothing
                break;
            case 1:
                result.put(directory.getName(), wars.get(0));
                break;
            default:
                throw new IOException(directory + ": wars ambiguous: " + wars);
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

    private Map<String, FileNode> lazyWars;

    public Map<String, FileNode> wars() throws IOException {
        if (lazyWars == null) {
            lazyWars = new HashMap<>();
            addWars(directory, lazyWars);
        }
        return lazyWars;
    }

    //-- stage name

    /**
     * The stage name has to be a valid domain name because is used as part of the application url.
     * See http://tools.ietf.org/html/rfc1035 section 2.3.1.
     */
    public static void checkName(String name) {
        char c;

        if (name.isEmpty()) {
            throw new ArgumentException("empty stage name is not allowed");
        }
        if (name.length() > 30) {
            //ITCA does not accept too long commonNames
            throw new ArgumentException("Stage Name is too long. Please take a shorter one.");
        }
        if (!isLetter(name.charAt(0))) {
            throw new ArgumentException("stage name does not start with a letter");
        }
        for (int i = 1; i < name.length(); i++) {
            c = name.charAt(i);
            if (!isValidStageNameChar(c)) {
                throw new ArgumentException("stage name contains illegal character: " + c);
            }
        }
    }

    public static boolean isValidStageNameChar(char c) {
        return isLetter(c) || isDigit(c) || c == '-' || c == '.';
    }
    // cannot use Character.is... because we check ascii only
    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
    // cannot use Character.is... because we check ascii only
    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static String nameForUrl(String url) {
        if (url.startsWith("gav:")) {
            return nameForGavUrl(url);
        } else if (url.startsWith("file:")) {
            return nameForFileUrl(url);
        } else {
            return nameForSvnOrGitUrl(url);
        }
    }

    private static String nameForGavUrl(String url) {
        int end;
        int start;

        url = one(url);
        end = url.lastIndexOf(':');
        if (end == -1) {
            return "stage";
        }
        start = url.lastIndexOf(':', end - 1);
        if (start == -1) {
            return "stage";
        }
        return url.substring(start + 1, end);
    }

    private static String nameForFileUrl(String url) {
        int idx;

        url = one(url);
        idx = url.lastIndexOf('/');
        if (idx == -1) {
            return "idx";
        }
        url = url.substring(idx + 1);
        idx = url.lastIndexOf('.');
        if (idx == -1) {
            return url;
        } else {
            return url.substring(0, idx);
        }
    }

    private static String one(String url) {
        int end;

        end = url.lastIndexOf(',');
        if (end != -1) {
            url = url.substring(0, end);
        }
        end = url.lastIndexOf('=');
        if (end != -1) {
            url = url.substring(0, end);
        }
        return url;
    }

    private static String nameForSvnOrGitUrl(String url) {
        String result;
        int idx;

        result = Strings.removeRightOpt(url, "/");
        idx = result.indexOf(':');
        if (idx != -1) {
            // strip protocol - important vor gav stages
            result = result.substring(idx + 1);
        }
        result = Strings.removeRightOpt(result, "/trunk");
        idx = result.lastIndexOf('/');
        result = result.substring(idx + 1); // ok for -1
        result = Strings.removeRightOpt(result, ".git");
        return result.isEmpty() ? "stage" : result;
    }
}
