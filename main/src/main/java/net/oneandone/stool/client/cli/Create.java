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
import net.oneandone.inline.Console;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.common.Reference;
import net.oneandone.stool.server.util.Server;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Create extends ProjectCommand {
    private final Map<String, String> config;

    public Create(World world, Console console, Server server, List<String> args) {
        super(world, console, server, eatProject(server.world, args));
        this.config = new LinkedHashMap<>();
        for (String arg : args) {
            property(arg);
        }
    }

    private static FileNode eatProject(World world, List<String> args) {
        String arg;

        if (!args.isEmpty()) {
            arg = args.get(0);
            if (!arg.contains("=")) {
                args.remove(0);
                return world.file(arg);
            }
        }
        return world.getWorking();
    }

    private void property(String str) {
        int idx;
        String key;
        String value;

        idx = str.indexOf('=');
        if (idx == -1) {
            throw new ArgumentException("Invalid configuration argument. Expected <key>=<value>, got " + str);
        }
        key = str.substring(0, idx);
        value = str.substring(idx + 1);
        if (config.put(key, value) != null) {
            throw new ArgumentException("already configured: " + key);
        }
    }

    @Override
    public void doRun(FileNode projectDirectory) throws IOException {
        Project project;
        String name;
        Reference reference;

        project = Project.lookup(projectDirectory);
        if (project == null) {
            project = Project.create(projectDirectory);
        } else {
            if (project.getAttachedOpt() != null) {
                throw new ArgumentException("project already has a stage");
            }
        }
        name = config.remove("name");
        if (name == null) {
            name = project.getDirectory().getName();
        }
        Project.checkName(name);
        reference = server.create(name, project.getOrigin(), config, console);
        project.setAttached(reference);
    }
}
