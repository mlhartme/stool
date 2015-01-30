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
package net.oneandone.sales.tools.stool.util;

import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** deletes all directories */
public class RmRfThread extends Thread {
    private final List<Node> dirs;
    private final Console console;

    public RmRfThread(Console console) {
        this.dirs = new ArrayList<>();
        this.console = console;
    }

    public void add(Node dir) {
        dirs.add(dir);
    }
    @Override
    public void run() {
        console.info.println("Cleaning up ...");
        for (Node dir : dirs) {
            try {
                console.verbose.println("trying to remove " + dir.getName());
                dir.deleteTreeOpt();
            } catch (IOException e) {
                console.verbose.println(e.getMessage());
            }
        }
        console.info.println("done.");
    }
}
