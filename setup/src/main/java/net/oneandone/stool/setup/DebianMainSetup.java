/**
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
package net.oneandone.stool.setup;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class DebianMainSetup extends Debian {
    public static void main(String[] args) throws IOException {
        System.exit(new DebianMainSetup().run(args));
    }

    //--

    private final FileNode bin;
    private final FileNode lib;
    private final String config;
    private final String group;

    public DebianMainSetup() throws IOException {
        super("stool");
        // this is not configurable, because the content comes from the package:
        bin = world.file("/usr/share/stool");

        lib = world.file(db_get("stool/lib"));
        config = db_get("stool/config");
        group = db_get("stool/group");
    }

    //--

    @Override
    public void postinstConfigure(String previous) throws IOException {
        FileNode binLib;

        setupGroup();
        setupLib();
        binLib = bin.join("lib");
        if (previous != null) {
            if (Files.deleteIfExists(binLib.toPath())) {
                log("cleaned previous lib: " + lib);
            }
        }
        lib.link(binLib);
        log("setup service:\n" + slurp("update-rc.d", "stool", "defaults"));
        log("start service:\n" + slurp("service", "stool", "start"));
    }

    @Override
    public void postrmRemove() throws IOException {
        log(slurp("service", "stool", "stop"));
    }

    @Override
    public void postrmUpgrade() throws IOException {
        // TODO: prevent new stool invocations
    }

    @Override
    public void postrmPurge() throws IOException {
        exec("update-rc.d", "stool", "remove"); // Debian considers this a configuration file!?
        bin.join("lib").deleteDirectory();
        bin.deleteTree();
    }

    //--

    private void setupGroup() throws IOException {
        List<String> result;

        if (test("getent", "group", group)) {
            log("group: " + group + " (existing)");
        } else {
            result = new ArrayList<>();
            exec("groupadd", group);
            for (FileNode dir : world.file("/home/").list()) {
                if (dir.isDirectory()) {
                    String name = dir.getName();
                    if (test("id", "-u", name)) {
                        result.add(name);
                        exec("usermod", "-a", "-G", group, name);
                    }
                }
            }
            log("group: " + group + " (created with " + Separator.SPACE.join(result) + ")");
        }
    }

    //--

    public void setupLib() throws IOException {
        Lib h;

        h = new Lib(console, lib, group, config.trim().isEmpty() ? null : config);
        if (lib.exists()) {
            h.upgrade();
            log("lib: " + lib.getAbsolute() + " (upgraded)");
        } else {
            console.info.println("creating lib: " + lib);
            try {
                h.create();
            } catch (IOException e) {
                // make sure we don't leave any undefined lib directory;
                try {
                    lib.deleteTreeOpt();
                } catch (IOException suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw e;
            }
        }
    }
}
