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

import net.oneandone.inline.Console;
import net.oneandone.stool.common.Reference;
import net.oneandone.stool.common.State;
import net.oneandone.stool.server.util.Server;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ClientCommand {
    protected final World world;
    protected final Console console;
    protected final Server server;

    public ClientCommand(World world, Console console, Server server) {
        this.console = console;
        this.world = world;
        this.server = server;
    }

    public void run() throws Exception {
        try {
            doRun();
        } finally {
            // TODO: move into server shutdown:
            //    server.session.closeDockerEngine();
        }
    }

    public abstract void doRun() throws Exception;

    protected void run(Launcher l, Node output) throws IOException {
        message(l, output instanceof FileNode ? " > " + output : "");
        runQuiet(l, output);
    }

    protected void runQuiet(Launcher l, Node output) throws IOException {
        try (Writer out = output.newWriter()) {
            l.exec(out);
        }
    }

    protected void header(String h) {
        console.info.println("[" + h + "]");
    }

    protected void message(Launcher l, String suffix) {
        message(Separator.SPACE.join(l.getBuilder().command()) + suffix);
    }

    protected void message(String msg) {
        console.info.println(Strings.indent(msg, "  "));
    }


    //-- utility code to simplify server api

    public Reference resolveName(String name) throws IOException {
        Map<String, IOException> problems;
        List<Reference> found;

        problems = new HashMap<>();
        found = server.search(name, problems);
        if (!problems.isEmpty()) {
            throw new IOException("cannot resolve name: " + problems.toString());
        }
        switch (found.size()) {
            case 0:
                throw new IOException("no such stage: " + name);
            case 1:
                return found.get(0);
            default:
                throw new IOException("stage ambiguous: " + name);
        }
    }

    public String getName(Reference reference) throws Exception {
        Map<String, String> properties;
        String result;

        properties = server.getProperties(reference);
        result = properties.get("name");
        if (result == null) {
            throw new IOException("missing 'name' property");
        } else {
            return result;
        }
    }

    public State state(Reference reference) throws IOException {
        Map<String, String> map;

        map = server.status(reference, Strings.toList("state"));
        if (map.size() != 1) {
            throw new IllegalStateException(map.toString());
        }
        return State.valueOf(map.values().iterator().next().toUpperCase());
    }

}
