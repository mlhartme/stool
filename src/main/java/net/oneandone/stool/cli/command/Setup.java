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
package net.oneandone.stool.cli.command;

import net.oneandone.inline.Console;
import net.oneandone.stool.cli.Context;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.Main;
import net.oneandone.stool.core.LocalSettings;
import net.oneandone.stool.core.Settings;
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
    private final FileNode home;
    private final String classpath;
    private final String lib;
    private final String registryCredentials;
    private final Console console;
    private final String version;
    private final String spec;

    public Setup(Globals globals, String classpath, String lib, String registryCredentials, String spec) {
        this.world = globals.getWorld();
        this.home = globals.home();
        this.classpath = classpath;
        this.lib = lib;
        this.registryCredentials = registryCredentials;
        this.console = globals.getConsole();
        this.version = Main.versionString(world);
        this.spec = spec;
    }

    public void run() throws IOException {
        Settings settings;

        if (home.exists()) {
            throw new IOException("Stool is already set up in " + home.getAbsolute());
        }
        settings = settings();
        home.mkdir();
        create("lib", lib);
        if (registryCredentials != null) {
            settings.registryCredentials.putAll(Settings.parseRegistryCredentials(registryCredentials));
        }
        settings.save(Settings.settingsYaml(home));
        console.info.println("Done - created " + home.getAbsolute() + " for Stool version " + version);
        console.info.println("Available contexts:");
        for (Context c : settings.proxies.values()) {
            console.info.println("  " + c.name + " " + c.url);
        }
        console.info.println();
        console.info.println("Use 'sc context <name>' to choose a current context.");
        console.info.println();
        console.info.println();
        console.info.println("If you want some rough shell completion: ");
        console.info.println("  Add 'eval \"$(sc shell-inc)\"' in your shell profile.");
        console.info.println("  Don't forget to restart your terminal.");
    }

    private void create(String name, String linkTo) throws IOException {
        FileNode dir;

        dir = home.join(name);
        if (linkTo != null) {
            world.file(home, linkTo).checkDirectory().link(dir);
        } else {
            dir.mkdir();
        }
    }

    private Settings settings() throws IOException {
        int idx;
        Settings result;
        String name;
        String url;

        result = initialConfiguration();
        if (classpath != null) {
            result.local.classpath.clear();
            result.local.classpath.addAll(LocalSettings.COLON.split(classpath));
        }
        if (spec != null) {
            idx = spec.indexOf('=');
            if (idx == -1) {
                throw new IllegalStateException("missing '=': " + spec);
            }
            name = spec.substring(0, idx);
            url = spec.substring(idx + 1);
            result.proxies.clear();
            result.addContext(name, url, null);
        }
        return result;
    }

    private Settings initialConfiguration() throws IOException {
        FileNode template;

        template = cisotoolsEnvironment(world);
        return template == null ? Settings.create(world) : Settings.load(home, template);
    }

    public static FileNode cisotoolsEnvironment(World world) throws FileNotFoundException, ExistsException {
        FileNode cisotools;

        cisotools = cisotools(world);
        return cisotools == null ? null : cisotools.join("stool/environment.yaml").checkFile();
    }
}
