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
package net.oneandone.stool.extensions;

import net.oneandone.inline.Console;
import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class Fault implements Extension {
    private String project;

    public Fault() {
        this.project = "";
    }

    @Override
    public Map<String, FileNode> vhosts(Stage stage) {
        return new HashMap<>();
    }

    @Override
    public void beforeStart(Stage stage) throws IOException {
    }

    // TODO: have a list of projects; always prepend @
    private String projects() {
        StringBuilder result;

        if (project.isEmpty()) {
            return project;
        }
        result = new StringBuilder();
        for (String entry : Separator.SPACE.split(project)) {
            if (result.length() > 0) {
                result.append(' ');
            }
            if (!entry.startsWith("@")) {
                result.append('@');
            }
            result.append(entry);
        }
        return result.toString();
    }

    @Override
    public void beforeStop(Stage stage) throws IOException {
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) {
    }

    @Override
    public void tomcatOpts(Stage stage, Map<String, String> result) {
        result.put("fault.workspace", "/etc/secrets");
    }

    @Override
    public void containerOpts(Stage stage, Map<String, Object> result) {
        result.put("fault", Boolean.TRUE);
        result.put("fault_project", projects());
    }

    @Override
    public void files(Stage stage, FileNode dir) throws IOException {
        Console console;
        Launcher launcher;

        launcher = stage.launcher("fault");
        console = stage.session.console;
        if (console.getVerbose()) {
            launcher.arg("-v");
        }
        launcher.arg("-auth=false");
        launcher.arg("run", projects(), "cp", "-r", dir.getWorld().getHome().join(".fault").getAbsolute(), dir.getAbsolute());
        console.verbose.println("executing " + launcher);
        console.verbose.println(launcher.exec());
    }
}
