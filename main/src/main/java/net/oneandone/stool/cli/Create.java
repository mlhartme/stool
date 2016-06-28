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
import net.oneandone.stool.configuration.Property;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.ArtifactStage;
import net.oneandone.stool.stage.SourceStage;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Create extends SessionCommand {
    private final boolean quiet;
    private String name;
    private String urlOrFileOrSearch;

    private FileNode directory;

    private final Map<Property, String> config = new LinkedHashMap<>();

    private StageConfiguration stageConfiguration;

    private final Map<String, Property> properties;

    public Create(Session session, boolean quiet, String name, String urlOrFileOrSearch, FileNode directory) {
        super(session, Mode.NONE);
        this.quiet = quiet;
        this.name = name;
        this.urlOrFileOrSearch = urlOrFileOrSearch;
        this.directory = directory;
        this.properties = StageConfiguration.properties(session.extensionsFactory);
    }


    public void property(String str) {
        int idx;
        String key;
        String value;
        Property property;

        idx = str.indexOf('=');
        if (idx == -1) {
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

        session.create(stage.backstage, name);
        console.info.println("stage created: " + name);
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
        for (int i = urls.size(); i > 0; --i) {
            console.info.println("[" + i + "] " + urls.get(i - 1));
        }
        while (true) {
            input = console.readline("Choose url [1-" + urls.size() + "]) or press ctrl-c to abort: \n");
            try {
                no = Integer.parseInt(input);
                return Strings.removeLeftOpt(Strings.removeLeftOpt(urls.get(no - 1), "scm:"), "svn:");
            } catch (NumberFormatException e) {
                console.info.println("invalid input: " + e.getMessage());
            }
        }
    }

    private void defaults(String url) throws IOException {
        FileNode surrounding;

        if (directory == null) {
            directory = world.getWorking().join(Stage.nameForUrl(url));
        }
        surrounding = session.findStageDirectory(directory);
        if (surrounding != null) {
            throw new ArgumentException("cannot create a stage within a stage: " + directory + " in " + surrounding);
        }
        if (directory.isDirectory()) {
            throw new ArgumentException("stage directory already exists: " + directory);
        }
        if (!directory.getParent().isDirectory()) {
            throw new ArgumentException("parent directory for new stage does not exist: " + directory.getParent());
        }
        if (session.configuration.shared) {
            checkPermissions(directory.getParent());
        }
        session.checkDiskFree();
        if (name == null) {
            name = directory.getName();
        }
        Stage.checkName(name);
        if (session.stageNames().contains(name)) {
            throw new ArgumentException("stage name already exists: " + name);
        }
        if (stageConfiguration == null) {
            stageConfiguration = session.createStageConfiguration(url);
        }
    }

    private void checkPermissions(FileNode node) throws IOException {
        PosixFileAttributes attributes;
        FileNode parent;

        attributes = java.nio.file.Files.readAttributes(node.toPath(), PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.permissions().contains(PosixFilePermission.GROUP_EXECUTE)
                && !attributes.permissions().contains(PosixFilePermission.OTHERS_EXECUTE)) {
            throw new IOException(node.getAbsolute() + ": missing execute permission for group or others. \n" +
                    "You cannot create a stage in this directory because only you can access it.\n" +
                    "Try to create your stage in a different directory.");
        }
        parent = node.getParent();
        if (parent != null && !parent.equals(node)) {
            checkPermissions(parent);
        }
    }

    private Stage create(String url) throws Exception {
        Stage stage;
        String prepare;

        Files.createSourceDirectory(console.verbose, directory, session.group());
        stage = stage(url);
        // CAUTION: create backstage before possible prepare commands -- e.g. pws already populates the local repository of the stage
        Files.createStoolDirectory(console.verbose, stage.backstage);

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
        for (Map.Entry<Property, String> entry : config.entrySet()) {
            entry.getKey().set(stage.config(), entry.getValue());
        }
        stage.initialize();
        return stage;
    }

    private Stage stage(String url) throws Exception {
        ArtifactStage artifactStage;
        Stage stage;

        if (ArtifactStage.isArtifact(url)) {
            artifactStage = new ArtifactStage(session, url, name, directory, stageConfiguration);
            artifactStage.populateDirectory(console);
            stage = artifactStage;
        } else {
            url = Strings.removeRightOpt(url, "/");
            console.info.println("checking out " + directory);
            session.scm(url).checkout(url, directory, quiet ? console.verbose : console.info);
            stage = SourceStage.forUrl(session, name, directory, url, stageConfiguration);
        }
        return stage;
    }
}
