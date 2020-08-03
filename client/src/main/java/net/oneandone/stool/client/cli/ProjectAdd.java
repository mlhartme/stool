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
package net.oneandone.stool.client.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.Reference;
import net.oneandone.stool.client.Source;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class ProjectAdd extends ClientCommand {
    private final boolean detached;
    private final Map<String, Source.Type> paths;
    private final String stage;

    public ProjectAdd(Globals globals, boolean detached, List<String> args) {
        super(globals);

        this.detached = detached;
        this.paths = new LinkedHashMap<>();
        if (args.isEmpty()) {
            throw new ArgumentException("missing name argument");
        }
        this.stage = args.remove(args.size() - 1);
        eatPaths(args);
    }

    private void eatPaths(List<String> args) {
        int idx;
        Source.Type type;

        if (args.isEmpty()) {
            paths.put("", Source.Type.WAR);
        } else {
            for (String arg : args) {
                idx = arg.indexOf('@');
                if (idx == -1) {
                    type = Source.Type.WAR;
                } else {
                    type = Source.Type.valueOf(arg.substring(0, idx).toUpperCase());
                    arg = arg.substring(idx + 1);
                }
                if (paths.put(arg, type) != null) {
                    throw new ArgumentException("duplicate path: " + arg);
                }
            }
        }
    }

    @Override
    public void run() throws IOException {
        Project projectOpt;
        Map<Source, String> map;
        String name;
        String path;

        projectOpt = lookupProject(working);
        if (projectOpt != null) { // TODO: feels weired
            throw new ArgumentException("project already has a stage; detach it first");
        }
        if (detached) {
            projectOpt = null;
        } else {
            projectOpt = Project.create(working);
        }
        try {
            map = new HashMap<>();
            for (Map.Entry<String, Source.Type> entry : paths.entrySet()) {
                path = entry.getKey();
                for (Source source : Source.findAndCheck(entry.getValue(), path.isEmpty() ? working : world.file(path), stage)) {
                    name = source.subst(stage);
                    if (map.values().contains(name)) {
                        throw new ArgumentException("duplicate name: " + name); // TODO: improved message
                    }
                    map.put(source, name);
                }
            }
            if (map.isEmpty()) {
                throw new ArgumentException("no sources found");
            }
            for (Map.Entry<Source, String> entry : map.entrySet()) {
                add(projectOpt, entry.getKey(), entry.getValue());
            }
            if (projectOpt != null) {
                projectOpt.save();
            }
        } catch (IOException e) {
            try {
                if (projectOpt != null) {
                    projectOpt.save();
                }
            } catch (IOException e2) {
                e.addSuppressed(e2);
            }
            throw e;
        }
    }

    private void add(Project projectOpt, Source source, String name) throws IOException {
        Reference reference;

        reference = stage(name);
        if (projectOpt != null) {
            try {
                projectOpt.add(source, reference);
            } catch (IOException e) {
                throw new IOException("failed to attach stage: " + e.getMessage(), e);
            }
        } else {
            // -detached
        }
    }

    protected abstract Reference stage(String name) throws IOException;
}
