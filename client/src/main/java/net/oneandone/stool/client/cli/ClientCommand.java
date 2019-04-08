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
import net.oneandone.stool.client.Client;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

public abstract class ClientCommand {
    protected final World world;
    protected final Console console;
    protected final Client client;

    public ClientCommand(World world, Console console, Client client) {
        this.console = console;
        this.world = world;
        this.client = client;
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

    public boolean up(String stage) throws IOException {
        Map<String, String> map;

        map = client.status(stage, Strings.toList("up"));
        if (map.size() != 1) {
            throw new IllegalStateException("unknown state: " + map.toString());
        }
        return Boolean.valueOf(map.values().iterator().next().toUpperCase());
    }
}
