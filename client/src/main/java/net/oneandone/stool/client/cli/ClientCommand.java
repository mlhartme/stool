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
import net.oneandone.stool.client.Workspace;
import net.oneandone.stool.client.Reference;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public abstract class ClientCommand {
    protected final Globals globals;
    protected final World world;
    protected final Console console;

    public ClientCommand(Globals globals) {
        FileNode scYaml;

        this.globals = globals;
        this.world = globals.getWorld();
        this.console = globals.getConsole();

        scYaml = globals.scYaml();
        if (!scYaml.exists()) {
            throw new ArgumentException("client configuration not found: " + scYaml + "\nRun 'sc setup' to create it.");
        }
    }

    public Workspace lookupWorkspace() throws IOException {
        return lookupWorkspace(world.getWorking());
    }

    public Workspace lookupWorkspace(FileNode directory) throws IOException {
        return Workspace.lookup(directory, globals.configuration());
    }

    public List<Reference> workspaceReferences() throws IOException {
        Workspace workspace;
        List<Reference> result;

        workspace = lookupWorkspace();
        result = new ArrayList<>();
        if (workspace != null) {
            result.addAll(workspace.references());
        }
        return result;
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
}
