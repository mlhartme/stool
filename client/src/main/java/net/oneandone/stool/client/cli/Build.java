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
import net.oneandone.stool.client.BuildResult;
import net.oneandone.stool.client.Globals;
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
    private final List<FileNode> explicitWars;
    private final Map<String, String> arguments;

    public Build(Globals globals, FileNode project, boolean noCache, int keep, boolean restart, String comment, List<String> warsAndArgs) throws IOException {
        super(globals, project);
        this.noCache = noCache;
        this.keep = keep;
        this.restart = restart;
        this.comment = comment;
        this.explicitWars = eatWars(globals.getWorld(), warsAndArgs);
        this.arguments = argument(warsAndArgs);
    }

    private static List<FileNode> eatWars(World world, List<String> wars) throws IOException {
        List<FileNode> result;
        String path;
        FileNode war;

        result = new ArrayList<>();
        while (!wars.isEmpty()) {
            if (wars.get(0).contains("=")) {
                break;
            }
            path = wars.remove(0);
            war = world.file(path);
            war.checkFile();
            result.add(war);
        }
        return result;
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

    @Override
    public void doRun(FileNode projectDirectory) throws Exception {
        Project project;
        Reference reference;
        List<FileNode> wars;
        BuildResult result;
        long started;

        project = Project.lookup(projectDirectory);
        if (project == null) {
            throw new ArgumentException("unknown stage");
        }
        wars = explicitWars.isEmpty() ? project.wars() : explicitWars;
        if (wars.isEmpty()) {
            throw new IOException("no war(s) to build");
        }
        reference = project.getAttachedOpt(globals.servers());
        if (reference == null) {
            throw new IOException("no stage attached to " + projectDirectory);
        }
        for (FileNode war : wars) {
            started = System.currentTimeMillis();
            console.info.println("building image for " + war + " (" + (war.size() / (1024 * 1024)) + " mb)");
            result = reference.client.build(reference.stage, war, comment, project.getOriginOrUnknown(), createdOn(), noCache, keep, arguments);
            if (result.error != null) {
                console.info.println("build failed: " + result.error);
                console.info.println("build output");
                console.info.println(result.output);
                throw new ArgumentException("build failed");
            } else {
                console.verbose.println(result.output);
            }
            console.info.println("done: image " + result.tag + " (" + (System.currentTimeMillis() - started) / 1000 + " seconds)");
            if (restart) {
                new Restart(globals, new ArrayList<>()).doRun(reference);
            }
        }
    }

    private static String createdOn() {
        try {
            return System.getProperty("user.name") + '@' + InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            return "unknown host: " + e.getMessage();
        }
    }
}
