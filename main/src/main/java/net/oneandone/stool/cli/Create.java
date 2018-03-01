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
package net.oneandone.stool.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.configuration.PropertyType;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.ArtifactStage;
import net.oneandone.stool.stage.SourceStage;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Create extends SessionCommand {
    private final boolean quiet;
    private String urlOrFileOrSearch;

    private FileNode directory;

    private final Map<PropertyType, String> config = new LinkedHashMap<>();

    private StageConfiguration stageConfiguration;

    private final Map<String, PropertyType> properties;

    public Create(Session session, boolean quiet, String urlOrFileOrSearch) {
        super(session, Mode.NONE);
        this.quiet = quiet;
        this.urlOrFileOrSearch = urlOrFileOrSearch;
        this.directory = null;
        this.properties = session.properties();
    }


    public void dirOrProperty(String str) {
        int idx;
        String key;
        String value;
        PropertyType property;

        idx = str.indexOf('=');
        if (idx == -1) {
            if (directory == null) {
                directory = world.file(str);
                return;
            }
            throw new ArgumentException("Invalid configuration argument. Expected <key>=<value>, got " + str);
        }
        key = str.substring(0, idx);
        value = str.substring(idx + 1);
        property = properties.get(key);
        if (property == null) {
            throw new ArgumentException("unknown property: " + key);
        }
        if (config.put(property, value) != null) {
            throw new ArgumentException("already configured: " + key);
        }
    }

    @Override
    public void doRun() throws Exception {
        String url;
        RmRfThread cleanup;
        Stage stage;

        url = url();
        defaults(url);
        cleanup = new RmRfThread(console);
        cleanup.add(directory);
        Runtime.getRuntime().addShutdownHook(cleanup);

        // if this method fails with an exception or it is aborted with ctrl-c, the shutdown hook is used to wipe things
        stage = create(url);

        Runtime.getRuntime().removeShutdownHook(cleanup);

        session.add(stage.backstage, stage.getId());
        session.logging.setStage(stage.getId(), stage.getName());
        console.info.println("stage created: " + stage.getName());
        session.cd(stage.getDirectory());
    }

    private String url() throws IOException {
        FileNode file;
        String substring;
        List<String> urls;
        String input;
        int no;

        file = world.file(urlOrFileOrSearch);
        if (file.isFile()) {
            return file.getUri().toString();
        }
        if (!urlOrFileOrSearch.startsWith("%")) {
            return Strings.removeRightOpt(urlOrFileOrSearch, "/");
        }
        console.info.println("Searching ...");
        substring = urlOrFileOrSearch.substring(1);
        urls = session.search(substring);
        if (urls.size() == 0) {
            throw new ArgumentException("not found: " + substring);
        }
        for (int i = 1; i <= urls.size(); i++) {
            console.info.println("[" + i + "] " + urls.get(i - 1));
        }
        while (true) {
            input = console.readline("Choose url [1-" + urls.size() + "]) or press ctrl-c to abort: ");
            try {
                no = Integer.parseInt(input);
                return Strings.removeLeftOpt(urls.get(no - 1), "scm:");
            } catch (NumberFormatException e) {
                console.info.println("invalid input: " + e.getMessage());
            }
        }
    }

    private void defaults(String url) throws IOException {
        PropertyType np;
        String name;
        FileNode surrounding;

        if (directory == null) {
            directory = world.getWorking().join(Stage.nameForUrl(url));
        }
        if (directory.isDirectory()) {
            throw new ArgumentException("stage directory already exists: " + directory);
        }
        surrounding = session.findStageDirectory(directory);
        if (surrounding != null) {
            throw new ArgumentException("cannot create a stage within a stage: " + directory.getAbsolute() + " in " + surrounding);
        }
        if (!directory.getParent().isDirectory()) {
            throw new ArgumentException("parent directory for new stage does not exist: " + directory.getParent());
        }
        session.checkDiskFree(directory.getParent());
        np = properties.get("name");
        name = config.get(np);
        if (name == null) {
            name = directory.getName();
            config.put(np, name);
        }
        Stage.checkName(name);
        if (session.stageNames().contains(name)) {
            throw new ArgumentException("stage name already exists: " + name);
        }
        if (stageConfiguration == null) {
            stageConfiguration = session.createStageConfiguration(url);
        }
    }

    private Stage create(String url) throws Exception {
        Stage stage;
        String prepare;

        directory.mkdir();
        stage = stage(url);
        stage.modify();

        // make sure to run in stage environment, e.g. to have proper repository settings
        prepare = stage.config().prepare;
        if (!prepare.isEmpty()) {
            Launcher l = stage.launcher(Strings.toArray(Separator.SPACE.split(prepare)));
            if (quiet) {
                l.exec();
            } else {
                l.exec(console.info);
            }
        }
        stage.tuneConfiguration();
        for (Map.Entry<PropertyType, String> entry : config.entrySet()) {
            entry.getKey().set(stage.config(), entry.getValue());
        }
        stage.initialize();
        return stage;
    }

    private Stage stage(String url) throws Exception {
        String id;
        ArtifactStage artifactStage;
        Stage stage;

        id = session.nextStageId();
        if (ArtifactStage.isArtifact(url)) {
            artifactStage = new ArtifactStage(session, url, id, directory, stageConfiguration);
            // create backstage BEFORE possible artifactory resolving because it might
            // already populates the local repository of the stage
            artifactStage.backstage.mkdir();
            artifactStage.populateDirectory(console);
            stage = artifactStage;
        } else {
            url = Strings.removeRightOpt(url, "/");
            console.info.println("checking out " + directory);
            session.scm(url).checkout(url, directory, quiet ? console.verbose : console.info);
            stage = SourceStage.forUrl(session, id, directory, url, stageConfiguration);
            // create backstage AFTER checkout -- git would reject none-empty target directories
            stage.backstage.mkdir();
        }
        return stage;
    }
}
