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
import net.oneandone.stool.client.Client;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.Reference;
import net.oneandone.stool.client.ServerManager;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Create extends ProjectCommand {
    private final String name;
    private final String server;
    private final Map<String, String> config;

    public Create(Globals globals, FileNode project, String nameAndServer, List<String> args) {
        super(globals, project);

        int idx;

        idx = nameAndServer.indexOf('@');
        if (idx == -1) {
            throw new ArgumentException("expected <name>@<server>, got " + nameAndServer);
        }
        this.name = nameAndServer.substring(0, idx);
        Project.checkName(name);
        this.server = nameAndServer.substring(idx + 1);
        this.config = new LinkedHashMap<>();
        for (String arg : args) {
            property(arg);
        }
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
        ServerManager serverManager;
        Project project;
        Client client;
        Reference reference;

        serverManager = globals.servers();
        client = serverManager.get(server).connect(world);
        client.create(name, config);
        project = Project.lookup(projectDirectory);
        reference = new Reference(client, name);
        if (project == null) {
            Project.create(projectDirectory, reference);
        } else {
            if (project.getAttachedOpt(serverManager) != null) {
                throw new ArgumentException("project already has a stage");
            }
            project.setAttached(reference);
        }
        console.info.println("stage create: " + reference);
    }
}
