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
package net.oneandone.stool.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Import extends SessionCommand {
    private int max;
    private String nameTemplate;

    private final List<FileNode> includes;
    private final List<FileNode> excludes;
    private String upgradeId;

    public Import(Session session) {
        super(session, Mode.EXCLUSIVE);
        includes = new ArrayList<>();
        excludes = new ArrayList<>();
        max = 40;
        nameTemplate = "%d";
    }

    public void setUpgradeId(String id) {
        upgradeId = id;
    }

    public void setMax(int max) {
        this.max = max;
    }

    public void setName(String name) {
        this.nameTemplate = name;
    }

    public void dirs(String directory) {
        boolean exclude;
        FileNode node;

        exclude = directory.startsWith("^");
        if (exclude) {
            directory = directory.substring(1);
        }
        node = world.file(directory);
        if (!node.isDirectory()) {
            throw new ArgumentException("no such directory: " + node.getAbsolute());
        }
        (exclude ? excludes : includes).add(node);
    }

    @Override
    public void doRun() throws IOException {
        List<Stage> found;
        List<FileNode> existing;
        Stage stage;

        found = new ArrayList<>();
        if (includes.size() == 0) {
            includes.add(world.getWorking());
        }
        existing = session.stageDirectories();

        for (FileNode directory : includes) {
            scan(directory, found, existing);
        }
        console.info.print("[" + found.size() + " candidates]\u001b[K\r");
        console.info.println();
        if (upgradeId != null && found.size() != 1) {
            throw new IOException("upgrade import failed: " + found.size());
        }
        switch (found.size()) {
            case 0:
                console.info.println("No stage candidates found.");
                break;
            case 1:
                stage = found.get(0);
                console.info.println("Importing " + stage.getDirectory());
                doImport(stage, null);
                session.cd(stage.getDirectory());
                break;
            default:
                interactiveImport(found);
                break;
        }
    }

    private void interactiveImport(List<Stage> candidates) {
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
        try {
            doImport(candidate, forceName);
            candidates.remove(candidate);
            console.info.println("imported: " + candidate.getName());
        } catch (IOException e) {
            console.info.println(candidate.getDirectory() + ": import failed: " + e.getMessage());
            e.printStackTrace(console.verbose);
        }
    }

    private void scan(FileNode parent, List<Stage> result, List<FileNode> existingStages) throws IOException {
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

        url = Stage.probe(parent);
        if (url == null) {
            if (!parent.join("pom.xml").isFile()) {
                for (FileNode child : parent.list()) {
                    scan(child, result, existingStages);
                    if (result.size() >= max) {
                        break;
                    }
                }
            }
        } else {
            stage = Stage.createOpt(session, upgradeId == null ? session.nextStageId() : upgradeId,
                    url, session.createStageConfiguration(url), parent);
            result.add(stage);
            if (result.size() >= max) {
                console.info.println("\n\nScan stopped - max number of import projects reached: " + max);
            }
        }
    }

    private void doImport(Stage stage, String forceName) throws IOException {
        FileNode directory;
        String name;

        directory = stage.getDirectory();
        Files.sourceTree(console.verbose, directory, session.group());
        name = forceName != null ? forceName : name(directory);
        Files.createStoolDirectory(session.console.verbose, Stage.backstageDirectory(directory));
        stage.config().name = name;
        stage.tuneConfiguration();
        stage.initialize();
        session.add(stage.backstage, stage.getId());
    }

    private String name(FileNode directory) {
        return nameTemplate.replace("%d", directory.getName());
    }
}
