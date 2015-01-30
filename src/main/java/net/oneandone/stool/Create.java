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

import net.oneandone.pommes.cli.Environment;
import net.oneandone.pommes.model.Database;
import net.oneandone.pommes.model.Pom;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.stage.ArtifactStage;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.stage.WarStage;
import net.oneandone.stool.stage.WorkspaceStage;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.stool.util.Role;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.cli.Value;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Strings;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Create extends SessionCommand {
    @Option("quiet")
    private boolean quiet = false;

    @Option("prefix")
    private int portPrefix = 0;

    @Option("name")
    private String name = null;

    @Value(name = "url", position = 1)
    private String urlOrSearch;

    private FileNode directory;

    private Map<String, String> config = new HashMap<>();

    private StageConfiguration stageConfiguration;

    public Create(Session session) {
        super(session);
    }

    public Create(Session session, boolean quiet, String name, String urlOrSearch, FileNode directory,
                  StageConfiguration stageConfiguration) {
        super(session);
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

        if (directory == null) {
            this.directory = world.file(str);
        } else {
            idx = str.indexOf('=');
            if (idx == -1) {
                throw new ArgumentException("Invalid configuration argument. Expected <key>=<value>, got " + str);
            }
            key = str.substring(0, idx);
            value = str.substring(idx + 1);

            if (!StageConfiguration.isConfigurable(key, null)) {
                throw new ArgumentException("unknown property: " + key);
            }
            if (config.containsKey(key)) {
                throw new ArgumentException("already configured: " + key);
            }
            config.put(key, value);
        }
    }

    @Override
    public void doInvoke() throws Exception {
        FileNode wrapper;
        Stage stage;
        String url;
        RmRfThread cleanup;

        if (!Role.isAdmin(session.configuration) && session.configuration.security.isWaterloo()) {
            throw new IOException("You don't have the permissions to do that. This incident will be reported.");
        }

        url = url();
        defaults(url);
        wrapper = session.wrappers.join(name);
        cleanup = new RmRfThread(console);
        cleanup.add(directory);
        cleanup.add(wrapper);
        Runtime.getRuntime().addShutdownHook(cleanup);

        // if this method fails with an exception or is aborted with ctrl-c, the shutdown hook is used to wipe things
        stage = create(wrapper, url);

        Runtime.getRuntime().removeShutdownHook(cleanup);

        console.info.println("stage created: " + name);
        if (session.getSelectedStageName() == null) {
            session.backupEnvironment();
        }
        session.select(stage);
    }

    private String url() throws IOException, URISyntaxException, QueryNodeException {
        Database database;
        String substring;
        List<String> urls;
        String input;
        int no;
        String url;

        if (!urlOrSearch.startsWith("%")) {
            return Strings.removeRightOpt(urlOrSearch, "/");
        }
        console.info.println("Searching pommes ...");
        substring = urlOrSearch.substring(1);
        database = Database.load(console.world).downloadOpt();
        urls = new ArrayList<>();
        for (Pom pom : database.query(substring, new Environment(console.world))) {
            url = pom.projectUrl();
            if (url != null) {
                url = Strings.removeRight(url, "/");
                urls.add(url);
            }
        }
        if (urls.size() == 0) {
            throw new ArgumentException("search not found: " + substring);
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

    private void defaults(String url) throws IOException, URISyntaxException, QueryNodeException {
        FileNode parent;

        if (directory == null) {
            directory = (FileNode) console.world.getWorking().join(Stage.nameForUrl(url));
        }
        parent = directory.getParent();
        if (session.stageDirectories().contains(parent)) {
            directory = parent.getParent().join(directory.getName());
            console.verbose.println("warning: cannot create a stage within a stage. Changing directory to " + directory.getAbsolute());
        }
        if (directory.isDirectory()) {
            throw new ArgumentException("stage directory already exists: " + directory);
        }
        if (!parent.isDirectory()) {
            throw new ArgumentException("parent directory for new stage does not exist: " + directory.getParent());
        }
        session.checkDiskFree();
        if (name == null) {
            name = directory.getName();
        }
        Stage.checkName(name);
        if (session.wrappers.join(name).exists()) {
            throw new ArgumentException("stage name already exists: " + name);
        }
        if (stageConfiguration == null) {
            stageConfiguration = session.createStageConfiguration(url, session.configuration.defaults);
        }
    }

    private Stage create(FileNode wrapper, String url) throws Exception {
        Stage stage;

        directory.mkdir();
        stage = stage(wrapper, url);
        directory.link(stage.anchor());
        stage.tuneConfiguration();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            stage.config().configure(entry.getKey(), entry.getValue());
        }
        stage.saveWrapper();
        Files.stoolNode(stage.shared().join("log").mkdirOpt().join("stool.log").mkfile());
        return stage;
    }

    private Stage stage(FileNode wrapper, String url) throws Exception {
        ArtifactStage artifactStage;
        Stage stage;
        Ports ports;

        if (ArtifactStage.isArtifact(url)) {
            artifactStage = new ArtifactStage(session, url, wrapper, directory, stageConfiguration);
            artifactStage.populateDirectory(console);
            stage = artifactStage;
        } else {
            url = Strings.removeRightOpt(url, "/");
            console.info.println("checking out " + directory);
            session.subversion().checkout(directory.getParent(), url, directory.getName(), quiet ? console.verbose : console.info);
            if (WarStage.isWarStage(directory)) {
                stage = WarStage.forUrl(session, wrapper, directory, url, stageConfiguration);
            } else {
                stage = new WorkspaceStage(session, wrapper, directory, url, stageConfiguration);
                console.info.println("opening workspace to " + directory);
                // 'pws open' needs M2_OPTS already (more precisely repo location)!
                // Select before Create? Doesn't work ... :/
                // BaseCommand.envVars() are still set on last selected - is this a problem?
                Launcher l = stage.launcher(directory.getParent(),
                        "pwsraw", "open", "-name", directory.getName(), "-existing", url);
                if (quiet) {
                    l.exec();
                } else {
                    l.exec(console.info);
                }
            }
        }
        if (portPrefix == 0) {
            stage.config().ports = session.createPortsForName(stage.getName());
        } else {
            Ports explicit;

            explicit = new Ports(portPrefix);
            ports = session.createPorts(explicit);
            if (ports.equals(explicit)) {
                throw new ArgumentException("port prefix already used: " + portPrefix);
            }
            stage.config().ports = ports;
        }
        wrapper.mkdir();
        return stage;
    }

}
