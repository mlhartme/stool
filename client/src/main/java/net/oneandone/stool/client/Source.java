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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** List of Apps. Represents .backstage */
public class Source {
    public static final String SUBST = "_";

    public static boolean hasSubst(String name) {
        return name.contains(SUBST);
    }

    public static List<Source> findWarsAndCheck(FileNode directory, String stage) throws IOException {
        List<Source> wars;

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

    public static List<Source> findWars(FileNode directory) throws IOException {
        List<Source> result;

        result = new ArrayList<>();
        addWars(directory, result);
        return result;
    }

    private static void addWars(FileNode directory, List<Source> result) throws IOException {
        FileNode war;

        war = warMatcher(directory);
        if (war != null) {
            result.add(new Source(directory, war));
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

    //--

    public final FileNode directory;
    public final FileNode war;

    public Source(FileNode directory, FileNode war) {
        this.directory = directory;
        this.war = war;
    }

    public String subst(String name) throws IOException {
        return name.replace(SUBST, app(war));
    }

    public static String app(FileNode war) throws IOException {
        String result;

        result = App.properties(war).get(App.APP_ARGUMENT);
        return result == null ? "app": result;
    }
}
