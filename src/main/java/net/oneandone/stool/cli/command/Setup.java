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

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.cli.Context;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.Main;
import net.oneandone.stool.core.LocalSettings;
import net.oneandone.stool.core.Settings;
import net.oneandone.stool.util.Misc;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Setup {
    private final World world;
    private final FileNode home;
    private final String proxyPrefix;
    private final Map<String, String> set;
    private final Console console;
    private final String version;

    public Setup(Globals globals, String proxyPrefix, List<String> settings) {
        this.world = globals.getWorld();
        this.home = globals.home();
        this.proxyPrefix = proxyPrefix;
        this.set = Misc.assignments(settings);
        this.console = globals.getConsole();
        this.version = Main.versionString(world);
    }

    public void run() throws IOException {
        Settings settings;

        if (home.exists()) {
            throw new IOException("Stool is already set up in " + home.getAbsolute());
        }
        settings = settings();
        home.mkdir();
        settings.local.getLib().mkdir();
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

    private Settings settings() throws IOException {
        int idx;
        Settings result;
        String key;
        String value;

        result = initialSettings();
        for (Map.Entry<String, String> entry : set.entrySet()) {
            key = entry.getKey();
            value = entry.getValue();
            switch (key) {
                case "registryCredentials":
                    result.local.registryCredentials.putAll(LocalSettings.parseRegistryCredentials(entry.getValue()));
                    break;
                case "proxies":
                    idx = value.indexOf('=');
                    if (idx == -1) {
                        throw new ArgumentException("proxies: missing '=': " + value);
                    }
                    result.proxies.clear();
                    result.addContext(value.substring(0, idx), value.substring(idx + 1), null);
                    break;
                default:
                    result.local.set(key, entry.getValue());
                    break;
            }
        }
        try {
            result.contexts();
        } catch (ArgumentException e) {
            throw new ArgumentException(e.getMessage() + "\nTry -proxy-prefix", e);
        }
        return result;
    }

    private Settings initialSettings() throws IOException {
        String path;

        path = System.getenv("SC_SETUP_SETTINGS");
        return path == null ? Settings.create(world) : Settings.load(home, proxyPrefix, world.file(path).checkFile());
    }
}
