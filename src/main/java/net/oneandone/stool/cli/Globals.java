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
import net.oneandone.stool.core.Configuration;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.UUID;

/** Global client stuff */
public class Globals {
    public static Globals create(Console console, World world, FileNode scHomeOpt, String command) {
        FileNode scHome;

        scHome = scHomeOpt != null ?  scHomeOpt : Configuration.scHome(world);
        return new Globals(console, world, scHome, UUID.randomUUID().toString(), command);
    }

    private final Console console;
    private final World world;
    private final FileNode scHome;
    private final String invocation;
    private final String command;
    private String context;
    private FileNode wirelog;

    public Globals(Console console, World world, FileNode scHome, String invocation, String command) {
        this.console = console;
        this.world = world;
        this.scHome = scHome;
        this.invocation = invocation;
        this.command = command;
        this.context = null;
        this.wirelog = null;
    }

    public Workspace workspaceLoadOrCreate(String name) throws IOException {
        FileNode file;

        file = workspaceFile(name);
        return file.exists() ? workspaceLoad(name) : new Workspace(file);
    }

    public Workspace workspaceLoad(String name) throws IOException {
        return Workspace.load(workspaceFile(name), configuration(), caller());
    }

    /** param @name has to start with an @ */
    public FileNode workspaceFile(String name) throws MkdirException {
        return scHome().join("workspaces").mkdirOpt() /* TODO */.join(Strings.removeLeft(name, "@") + ".yaml");
    }

    public Caller caller() {
        return new Caller(invocation, "todoUser", command, wirelog); // TODO: immutable instance
    }

    public FileNode scHome() {
        return scHome;
    }

    public FileNode scYaml() {
        return Configuration.scYaml(scHome);
    }

    public void setWirelog(String wirelog) {
        this.wirelog = wirelog == null ? null : world.file(wirelog);
    }

    public void setException(boolean exception) {
        if (exception) {
            throw new RuntimeException("intentional exception");
        }
    }

    public void setContext(String context) {
        this.context = context;
    }

    public World getWorld() {
        return world;
    }

    public Console getConsole() {
        return console;
    }

    public Configuration configuration() throws IOException {
        Configuration result;

        result = Configuration.load(scHome, Configuration.scYaml(scHome));
        if (context != null) {
            result.setCurrentContext(context);
        }
        return result;
    }

    public Configuration configurationOrDefaults() throws IOException {
        return scHome().exists() ? configuration() : Configuration.create(world);
    }
}
