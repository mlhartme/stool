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
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Import extends SessionCommand {
    @Option("max")
    private int max = 40;

    @Option("name")
    private String nameTemplate = "%d";

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
        Stage stage;
        FileNode tempWrapper;

        found = new ArrayList<>();
        if (includes.size() == 0) {
            includes.add((FileNode) console.world.getWorking());
        }
        existing = session.stageDirectories();

        tempWrapper = session.wrappers.createTempDirectory();
        try {
            for (FileNode directory : includes) {
                scan(tempWrapper, directory, found, existing);
            }
        } finally {
            tempWrapper.deleteDirectory();
        }
        console.info.print("[" + found.size() + " candidates]\u001b[K\r");
        console.info.println();
        switch (found.size()) {
            case 0:
                console.info.println("No stage candidates found.");
                break;
            case 1:
                stage = found.get(0);
                console.info.println("Importing " + stage.getDirectory());
                stage = doImport(stage, null);
                new Select(session).stageToSelect(stage.getName()).invoke();
                break;
            default:
                interactiveImport(found);
                break;
        }
    }

    private void interactiveImport(List<Stage> candidates) throws IOException {
        Stage candidate;
        String str;
        int n;
        int idx;
        String name;

        while (true) {
            if (candidates.size() == 0) {
                console.info.println("Done - no more stage candidates");
                return;
            }
            for (int i = 0; i < candidates.size(); i++) {
                candidate = candidates.get(i);
                console.info.println("[" + (i + 1) + "] " + candidate.getDirectory() + "\t" + candidate.getUrl());
            }
            console.info.println("[<number> <name>] to import with the specified name");
            console.info.println("[a] all of the above");
            console.info.println("[q] quit - none of the above");
            str = console.readline("Please select: ").toLowerCase().trim();
            if ("q".equals(str)) {
                return;
            } else if ("a".equals(str)) {
                for (Stage f : new ArrayList<>(candidates)) {
                    importEntry(candidates, f, null);
                }
            } else {
                idx = str.indexOf(' ');
                if (idx != -1) {
                    name = str.substring(idx + 1).trim();
                    str = str.substring(0, idx);
                } else {
                    name = null;
                }
                try {
                    n = Integer.parseInt(str) - 1;
                } catch (NumberFormatException e) {
                    console.info.println("invalid input: " + str);
                    continue;
                }
                importEntry(candidates, candidates.get(n), name);
            }
        }
    }

    private void importEntry(List<Stage> candidates, Stage candidate, String forceName) {
        Stage stage;

        try {
            stage = doImport(candidate, forceName);
            candidates.remove(candidate);
            console.info.println("imported: " + stage.getName());
        } catch (IOException e) {
            console.info.println(candidate.getDirectory() + ": import failed: " + e.getMessage());
            e.printStackTrace(console.verbose);
        }
    }

    /** @return stages with temporary wrapper directory */
    private void scan(FileNode tmpWrapper, FileNode parent, List<Stage> result, List<FileNode> existingStages) throws IOException {
        String url;
        Stage stage;

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

        url = Stage.probe(session.subversion(), parent);
        if (url == null) {
            stage = null;
        } else {
            stage = Stage.createOpt(session, url, session.createStageConfiguration(url), tmpWrapper, parent);
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
                    scan(tmpWrapper, child, result, existingStages);
                    if (result.size() >= max) {
                        break;
                    }
                }
            }
        }
    }

    private Stage doImport(Stage candidate, String forceName) throws IOException {
        FileNode wrapper;
        String url;
        FileNode directory;
        Stage stage;

        url = candidate.getUrl();
        directory = candidate.getDirectory();
        wrapper = session.wrappers.join(forceName != null ? forceName : name(directory));
        stage = Stage.createOpt(session, url, session.createStageConfiguration(url), wrapper, directory);
        stage.tuneConfiguration();
        Files.stoolDirectory(stage.wrapper.mkdir());
        stage.saveWrapper();
        stage.getDirectory().link(stage.anchor());
        return stage;
    }

    private String name(FileNode directory) {
        return nameTemplate.replace("%d", directory.getName());
    }
}
