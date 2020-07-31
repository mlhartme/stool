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
import net.oneandone.stool.docker.Daemon;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** List of Apps. Represents .backstage */
public abstract class Source {
    public enum Type {
        WAR, DOCKER
    }

    public static final String SUBST = "_";

    public static boolean hasSubst(String name) {
        return name.contains(SUBST);
    }

    //--

    public static List<? extends Source> findAndCheck(Type type, FileNode directory, String stage) throws IOException {
        List<? extends Source> wars;

        directory.checkDirectory();
        switch (type) {
            case WAR:
                wars = WarSource.findWars(directory);
                break;
            case DOCKER:
                throw new IllegalStateException("TODO");
            default:
                throw new IllegalStateException();
        }
        if (wars.isEmpty()) {
            throw new ArgumentException(directory.getAbsolute() + ": no wars found - did you build your project?");
        } else if (wars.size() > 1) {
            if (!stage.contains(SUBST)) {
                throw new ArgumentException(stage + ": missing '" + SUBST + "' in stage name to attach " + wars.size() + " stages");
            }
        }
        return wars;
    }

    //--

    public final FileNode directory;

    public Source(FileNode directory) {
        this.directory = directory;
    }

    public abstract String subst(String name) throws IOException;

    public abstract String build(Globals globals, String comment, int keep, boolean noCache,
                        Daemon engine, Reference reference, String originScm, Map<String, String> explicitArguments)
            throws Exception;
}
