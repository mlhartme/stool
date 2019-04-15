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
import net.oneandone.stool.client.BuildResult;
import net.oneandone.stool.client.Client;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.Reference;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Build extends ProjectCommand {
    private final boolean noCache;
    private final int keep;
    private final boolean restart;
    private final String comment;
    private final Map<String, String> arguments;

    public Build(Globals globals, boolean noCache, int keep, boolean restart, String comment, List<String> projectOrArgs) {
        super(globals, eatProjectOpt(globals.world, projectOrArgs));
        this.noCache = noCache;
        this.keep = keep;
        this.restart = restart;
        this.comment = comment;
        this.arguments = argument(projectOrArgs);
    }

    private static Map<String, String> argument(List<String> args) {
        int idx;
        Map<String, String> result;

        result = new HashMap<>();
        for (String arg : args) {
            idx = arg.indexOf('=');
            if (idx == -1) {
                throw new ArgumentException("invalid argument: <key>=<value> expected, got " + arg);
            }
            result.put(arg.substring(0, idx), arg.substring(idx + 1));
        }
        return result;
    }

    private static FileNode eatProjectOpt(World world, List<String> lst) {
        String str;

        if (lst.size() > 0) {
            str = lst.get(0);
            if (!str.contains("=")) {
                lst.remove(0);
                return world.file(str);
            }
        }
        return null;
    }

    @Override
    public void doRun(FileNode projectDirectory) throws Exception {
        Project project;
        Client client;
        String stage;
        Map<String, FileNode> wars;
        BuildResult result;

        project = Project.lookup(projectDirectory);
        if (project == null) {
            throw new ArgumentException("unknown stage");
        }
        wars = project.wars();
        if (wars.isEmpty()) {
            throw new IOException("no wars to build");
        }
        stage = project.getAttachedOpt();
        if (stage == null) {
            throw new IOException("no stage attached to " + projectDirectory);
        }
        client = globals.client();
        for (Map.Entry<String, FileNode> entry : wars.entrySet()) {
            console.info.println(entry.getKey() + ": building image for " + entry.getValue());
            result = client.build(stage, entry.getKey(), entry.getValue(), comment, project.getOrigin(),
                    createdBy(), createdOn(), noCache, keep, arguments);
            project.imageLog().writeString(result.output);
            if (result.error != null) {
                console.info.println("build failed: " + result.error);
                console.info.println("build output");
                console.info.println(result.output);
                throw new ArgumentException("build failed");
            } else {
                console.verbose.println(result.output);
            }
            if (restart) {
                new Restart(globals, new ArrayList<>()).doRun(new Reference(client, stage));
            }
        }
    }

    private String createdBy() {
        return System.getProperty("user.name");
    }

    private String createdOn() {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            return "unknown host: " + e.getMessage();
        }
    }
}
