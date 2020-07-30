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
import net.oneandone.stool.client.Context;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Configuration;
import net.oneandone.sushi.fs.ExistsException;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Setup {
    public static FileNode cisotools(World world) {
        String path;

        path = System.getenv("CISOTOOLS_HOME");
        return path == null ? null : world.file(path);
    }

    private final World world;
    private final FileNode stoolYaml;
    private final Console console;
    private final String version;
    private final String spec;

    public Setup(Globals globals, String spec) {
        this.world = globals.getWorld();
        this.stoolYaml = globals.getStoolYaml();
        this.console = globals.getConsole();
        this.version = Main.versionString(world);
        this.spec = spec;
    }

    public void run() throws IOException {
        Configuration configuration;

        if (stoolYaml.exists()) {
            throw new IOException("Stool is already set up in " + stoolYaml.getAbsolute());
        }
        configuration = configuration();
        configuration.save(stoolYaml);
        console.info.println("Done - created " + stoolYaml.getAbsolute() + " for Stool version " + version);
        console.info.println("Available contexts:");
        for (Context c : configuration.contexts.values()) {
            console.info.println("  " + c.name + " " + c.url);
        }
        console.info.println();
        console.info.println("Use 'stool context <name>' to choose a current context.");
        console.info.println();
        console.info.println();
        console.info.println("If you want shell completion and a stage indicator in prompt: ");
        console.info.println("  Make sure to run 'eval \"$(sc shell-inc)\"' in your shell profile.");
        console.info.println("  Don't forget to restart your terminal.");
    }

    private Configuration configuration() throws IOException {
        int idx;
        Configuration result;
        String name;
        String url;
        String registryPrefix;

        result = initialConfiguration();
        if (spec != null) {
            idx = spec.indexOf('=');
            if (idx == -1) {
                throw new IllegalStateException("missing '=': " + spec);
            }
            name = spec.substring(0, idx);
            url = spec.substring(idx + 1);
            idx = url.indexOf('@');
            if (idx == -1) {
                throw new IllegalStateException("missing '@': " + spec);
            }
            registryPrefix = url.substring(idx + 1);
            url = url.substring(0, idx);

            result.contexts.clear();
            result.addContext(name, url, null);
            result.setRegistryPrefix(registryPrefix);
        }
        return result;
    }

    private Configuration initialConfiguration() throws IOException {
        FileNode template;
        Configuration result;

        template = cisotoolsEnvironment(world);
        result = new Configuration(world);
        if (template != null) {
            result.load(template);
        }
        return result;
    }

    public static FileNode cisotoolsEnvironment(World world) throws FileNotFoundException, ExistsException {
        FileNode cisotools;

        cisotools = cisotools(world);
        return cisotools == null ? null : cisotools.join("stool/stool.yaml").checkFile();
    }
}
