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
package net.oneandone.stool.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.oneandone.inline.Console;
import net.oneandone.sushi.fs.DirectoryNotFoundException;
import net.oneandone.sushi.fs.ExistsException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.UUID;

/** Global client stuff */
public class Globals {
    public static Globals create(Console console, World world, FileNode stoolYamlOpt, String command) {
        FileNode stoolYaml;
        String str;

        if (stoolYamlOpt != null) {
            stoolYaml = stoolYamlOpt;
        } else {
            str = System.getenv("STOOL_YAML");
            if (str != null) {
                stoolYaml = world.file(str);
            } else {
                stoolYaml = world.getHome().join(".stool.yaml");
            }
        }
        return new Globals(console, world, stoolYaml, UUID.randomUUID().toString(), command);
    }

    private final Console console;
    private final World world;
    private final Gson gson;
    private final FileNode stoolYaml;
    private final String invocation;
    private final String command;
    private String context;
    private FileNode wirelog;

    public Globals(Console console, World world, FileNode stoolYaml, String invocation, String command) {
        this.console = console;
        this.world = world;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.stoolYaml = stoolYaml;
        this.invocation = invocation;
        this.command = command;
        this.context = context;
        this.wirelog = null;
    }

    public FileNode getStoolYaml() {
        return stoolYaml;
    }

    public FileNode templates() throws ExistsException, DirectoryNotFoundException {
        return world.file(System.getenv("CISOTOOLS_HOME")).join("stool/templates-5").checkDirectory(); // TODO
    }

    public void setWirelog(String wirelog) {
        this.wirelog = wirelog == null ? null : world.file(wirelog);
    }

    public void setContext(String context) {
        this.context = context;
    }

    public void setException(boolean exception) {
        if (exception) {
            throw new RuntimeException("intentional exception");
        }
    }

    public Gson getGson() {
        return gson;
    }

    public World getWorld() {
        return world;
    }

    public Console getConsole() {
        return console;
    }

    public Configuration configuration() throws IOException {
        Configuration result;

        result = new Configuration(world, wirelog, invocation, command);
        result.load(getStoolYaml());
        if (context != null) {
            result.setCurrentContext(context);
        }
        return result;
    }
}
