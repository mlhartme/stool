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
package net.oneandone.stool.cli;

import net.oneandone.inline.Console;
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.stage.State;
import net.oneandone.stool.util.Info;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Server;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public abstract class ClientCommand {
    protected final Console console;
    protected final World world;
    protected final Server server;

    public ClientCommand(Server server) {
        this.console = server.console;
        this.world = server.world;
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

    public String getName(Reference reference) throws Exception {
        List<Property> properties;

        properties = server.getProperties(reference);
        for (Property property : properties) {
            if ("name".equals(property.name())) {
                return property.get();
            }
        }
        throw new IOException("missing 'name' property");
    }

    public State state(Reference reference) throws IOException {
        List<Info> lst;

        lst = server.status(reference, Strings.toList("state"));
        if (lst.size() != 1) {
            throw new IllegalStateException(lst.toString());
        }
        return State.valueOf(lst.get(0).get().toString().toUpperCase());
    }

}
