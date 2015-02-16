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
package net.oneandone.stool;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Import extends SessionCommand {
    @Option("max")
    private int max = 40;

    private final List<FileNode> includes;
    private final List<FileNode> excludes;

    public Import(Session session) {
        super(session);
        includes = new ArrayList<>();
        excludes = new ArrayList<>();
    }

    @Remaining
    public void dirs(String directory) throws IOException {
        boolean exclude;
        FileNode node;

        exclude = directory.startsWith("^");
        if (exclude) {
            directory = directory.substring(1);
        }
        node = world.file(directory);
        node.checkDirectory();
        (exclude ? excludes : includes).add(node);
    }

    @Override
    public void doInvoke() throws Exception {
        List<Stage> found;
        List<FileNode> existing;
        String str;
        int n;
        Stage stage;

        found = new ArrayList<>();
        if (includes.size() == 0) {
            includes.add((FileNode) console.world.getWorking());
        }
        existing = session.stageDirectories();

        for (FileNode directory : includes) {
            scan(directory, found, existing);
        }
        console.info.print("[" + found.size() + " candidates]\u001b[K\r");
        console.info.println();
        switch (found.size()) {
            case 0:
                console.info.println("No stage candidates.");
                return;
            case 1:
                stage = found.get(0);
                console.info.println("Importing stage " + stage.getName());
                doImport(stage);
                new Select(session).stageToSelect(stage.getName()).invoke();
                return;
            default:
                for (int i = 0; i < found.size(); i++) {
                    stage = found.get(i);
                    console.info.println("[" + (i + 1) + "] " + stage.getName() + "\t" + stage.getUrl());
                }
                while (true) {
                    str = console.readline("[number] to import directory, [a] to import all, or [q] to quit: ").toLowerCase();
                    if ("q".equals(str)) {
                        return;
                    }
                    if ("a".equals(str)) {
                        for (Stage f : found) {
                            if (f != null) {
                                doImport(f);
                            }
                        }
                        console.info.println("done");
                        return;
                    } else {
                        try {
                            n = Integer.parseInt(str) - 1;
                        } catch (NumberFormatException e) {
                            console.info.println("invalid input: " + str);
                            continue;
                        }
                        stage = found.get(n);
                        if (stage == null) {
                            console.info.println("already imported");
                        } else {
                            doImport(stage);
                            found.set(n, null);
                            console.info.println("done: " + stage.getName());
                        }
                    }
                }
        }
    }

    private void scan(FileNode parent, List<Stage> result, List<FileNode> existingStages) throws IOException {
        String url;
        Stage stage;
        FileNode wrapper;
        String name;

        console.info.print("[" + result.size() + " candidates] scanning " + parent + " ...\u001b[K\r");
        console.info.flush();
        if (!parent.isDirectory()) {
            return;
        }
        if (excludes.contains(parent)) {
            return;
        }
        if (parent.getName().startsWith(".")) {
            return;
        }
        if (existingStages.contains(parent)) {
            // already imported
            return;
        }

        name = parent.getName();
        try {
            Stage.checkName(name);
        } catch (ArgumentException e) {
            name = "imported-" + result.size();
        }
        wrapper = session.wrappers.join(name);
        wrapper.checkNotExists();
        url = Stage.probe(parent);
        if (url == null) {
            stage = null;
        } else {
            stage = Stage.createOpt(session, url, session.stoolConfiguration.createStageConfiguration(url), wrapper, parent);
        }
        if (stage != null) {
            // bingo
            result.add(stage);
            if (result.size() >= max) {
                console.info.println("\n\nScan aborted - max number of import projects reached: " + max);
            }
        } else {
            if (!parent.join("pom.xml").isFile()) {
                for (FileNode child : parent.list()) {
                    scan(child, result, existingStages);
                    if (result.size() >= max) {
                        break;
                    }
                }
            }
        }
    }

    private void doImport(Stage stage) throws IOException {
        stage.tuneConfiguration();
        Files.stoolDirectory(stage.wrapper.mkdir());
        stage.saveWrapper();
        stage.getDirectory().link(stage.anchor());
    }
}
