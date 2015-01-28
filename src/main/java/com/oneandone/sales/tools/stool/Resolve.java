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
package com.oneandone.sales.tools.stool;

import net.oneandone.maven.embedded.Maven;
import net.oneandone.sushi.cli.Command;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.cli.Value;
import net.oneandone.sushi.fs.file.FileNode;

import java.util.ArrayList;
import java.util.List;


public class Resolve implements Command {
    private final Console console;

    @Value(name = "gav", position = 1)
    private String gav;

    private List<FileNode> copies = new ArrayList<>();

    @Remaining
    public void remaining(String name) {
        copies.add(console.world.file(name));
    }

    public Resolve(Console console) {
        this.console = console;
    }

    @Override
    public void invoke() throws Exception {
        Maven maven;
        FileNode file;

        maven = Maven.withSettings(console.world);
        file = maven.resolve(gav);
        console.info.println("resolved: " + file);
        for (FileNode copy : copies) {
            file.copyFile(copy);
            if (file.getName().endsWith(".sh")) {
                copy.setPermissions("rwxr-xr-x");
            }
        }
    }
}
