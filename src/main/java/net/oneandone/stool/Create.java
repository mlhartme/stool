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

import net.oneandone.stool.configuration.Property;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.stage.ArtifactStage;
import net.oneandone.stool.stage.SourceStage;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.cli.Value;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Create extends SessionCommand {
    @Option("quiet")
    private boolean quiet = false;

    @Option("name")
    private String name = null;

    @Value(name = "url", position = 1)
    private String urlOrSearch;

    private FileNode directory;

    private Map<Property, String> config = new LinkedHashMap<>();

    private StageConfiguration stageConfiguration;

    private final Map<String, Property> properties;

    public Create(Session session) {
        super(session);
        this.properties = StageConfiguration.properties(session.extensionsFactory);
    }

    public Create(Session session, boolean quiet, String name, String urlOrSearch, FileNode directory,
                  StageConfiguration stageConfiguration) {
        this(session);
        this.quiet = quiet;
        this.name = name;
        this.urlOrSearch = urlOrSearch;
        this.directory = directory;
        this.stageConfiguration = stageConfiguration;
    }

    @Remaining
    public void remaining(String str) {
        int idx;
        String key;
        String value;
        Property property;

        if (directory == null) {
            this.directory = world.file(str);
        } else {
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
    }

    @Override
    public void doInvoke() throws Exception {
        FileNode backstage;
        Stage stage;
        String url;
        RmRfThread cleanup;

        url = url();
        defaults(url);
        backstage = session.backstages.join(name);
        cleanup = new RmRfThread(console);
        cleanup.add(directory);
        cleanup.add(backstage);
        Runtime.getRuntime().addShutdownHook(cleanup);

        // if this method fails with an exception or is aborted with ctrl-c, the shutdown hook is used to wipe things
        stage = create(backstage, url);

        Runtime.getRuntime().removeShutdownHook(cleanup);

        console.info.println("stage created: " + name);
        if (session.getSelectedStageName() == null) {
            session.backupEnvironment();
        }
        session.select(stage);
    }

    private String url() throws IOException {
        String substring;
        List<String> urls;
        String input;
        int no;

        if (!urlOrSearch.startsWith("%")) {
            return Strings.removeRightOpt(urlOrSearch, "/");
        }
        console.info.println("Searching pommes ...");
        substring = urlOrSearch.substring(1);
        urls = pommes(console.world, substring);
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
                return urls.get(no - 1);
            } catch (NumberFormatException e) {
                console.info.println("invalid input: " + e.getMessage());
            }
        }
    }

    private void defaults(String url) throws IOException {
        FileNode parent;

        if (directory == null) {
            directory = (FileNode) console.world.getWorking().join(Stage.nameForUrl(url));
        }
        parent = directory.getParent();
        if (session.stageDirectories().contains(parent)) {
            directory = parent.getParent().join(directory.getName());
            console.verbose.println("warning: cannot create a stage within a stage. Changing directory to " + directory.getAbsolute());
        }
        if (directory.hasDifferentAnchestor(session.backstages)) {
            throw new ArgumentException("you cannot create a stage in the backstages directory");
        }
        if (directory.isDirectory()) {
            throw new ArgumentException("stage directory already exists: " + directory);
        }
        if (!parent.isDirectory()) {
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
        if (session.backstages.join(name).exists()) {
            throw new ArgumentException("stage name already exists: " + name);
        }
        if (stageConfiguration == null) {
            stageConfiguration = session.createStageConfiguration(url);
        }
    }

    private void checkPermissions(FileNode node) throws IOException {
        PosixFileAttributes attributes;
        FileNode parent;

        attributes = java.nio.file.Files.readAttributes(node.toPath(), PosixFileAttributes.class, new LinkOption[]{LinkOption.NOFOLLOW_LINKS});
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

    private Stage create(FileNode backstage, String url) throws Exception {
        Stage stage;

        Files.createStageDirectory(console.verbose, directory, session.group());
        // CAUTION: create backstage before running possible prepare commands -- e.g. pws already populates the local repository of the stage
        Files.createBackstageDirectory(console.verbose, backstage);
        stage = stage(backstage, url);
        stage.tuneConfiguration();
        for (Map.Entry<Property, String> entry : config.entrySet()) {
            entry.getKey().set(stage.config(), entry.getValue());
        }
        stage.initialize();
        return stage;
    }

    private Stage stage(FileNode backstage, String url) throws Exception {
        ArtifactStage artifactStage;
        Stage stage;
        String prepare;

        if (ArtifactStage.isArtifact(url)) {
            artifactStage = new ArtifactStage(session, url, backstage, directory, stageConfiguration);
            artifactStage.populateDirectory(console);
            stage = artifactStage;
        } else {
            url = Strings.removeRightOpt(url, "/");
            console.info.println("checking out " + directory);
            session.subversion().checkout(directory.getParent(), url, directory.getName(), quiet ? console.verbose : console.info);
            stage = SourceStage.forUrl(session, backstage, directory, url, stageConfiguration);
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
        }
        return stage;
    }

    public static List<String> pommes(World world, String search) throws IOException {
        FileNode working;
        List<String> result;

        working = (FileNode) world.getWorking();
        result = new ArrayList<>();
        for (String line : Separator.RAW_LINE.split(working.exec("bash", "--login", "-c", "pommes find -format %o " + search))) {
            line = line.trim();
            line = Strings.removeRightOpt(line.trim(), "/pom.xml");
            line = Strings.removeLeftOpt(line, "svn:");
            result.add(line);
        }
        return result;
    }
}
