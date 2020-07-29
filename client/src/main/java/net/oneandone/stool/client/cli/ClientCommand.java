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
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.Reference;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

public abstract class ClientCommand {
    protected final Globals globals;
    protected final World world;
    protected final Console console;
    protected FileNode working;

    public ClientCommand(Globals globals) {
        FileNode stoolJson;

        this.globals = globals;
        this.world = globals.getWorld();
        this.console = globals.getConsole();
        this.working = globals.getWorld().getWorking();

        stoolJson = globals.getStoolJson();
        if (!stoolJson.exists()) {
            throw new ArgumentException("Stool configuration not found: " + stoolJson + "\nRun 'stool setup' to create it.");
        }
    }

    public void setWorkingOpt(FileNode workingOpt) { // TODO: why does inline call this method?
        if (workingOpt != null) {
            this.working = workingOpt;
        }
    }

    public Project lookupProject(FileNode directory) throws IOException {
        return Project.lookup(directory, globals.configuration());
    }

    public abstract void run() throws Exception;

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

    public boolean up(Reference reference) throws IOException {
        return !status(reference, "running").isEmpty();
    }

    private String status(Reference reference, String field) throws IOException {
        Map<String, String> map;

        map = reference.client.status(reference.stage, Strings.toList(field));
        if (map.size() != 1) {
            throw new IllegalStateException("unexpected status: " + map.toString());
        }
        return map.values().iterator().next();
    }
}
