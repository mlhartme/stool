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
package net.oneandone.stool.stage;

import net.oneandone.stool.Main;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.stage.artifact.Application;
import net.oneandone.stool.stage.artifact.Applications;
import net.oneandone.stool.stage.artifact.ArtifactSource;
import net.oneandone.stool.stage.artifact.Change;
import net.oneandone.stool.stage.artifact.Changes;
import net.oneandone.stool.stage.artifact.Inbox;
import net.oneandone.stool.stage.artifact.Maven;
import net.oneandone.stool.stage.artifact.Overview;
import net.oneandone.stool.stage.artifact.WarFile;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.MoveException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Run WAR-Artifacts without sources (instead of projects which are packaged as WARs).
 */
public class ArtifactStage extends Stage {
    private final Applications applications;

    public ArtifactStage(Session session, String url, FileNode wrapper, FileNode directory, StageConfiguration configuration)
      throws IOException {
        super(session, url, wrapper, directory, configuration);

        DefaultArtifact artifact;
        Set<String> names;
        String[] coords;
        String artifactId;
        String name;
        int idx;
        applications = new Applications();
        names = new HashSet<>();

        for (String gav : getGavs().split(",")) {
            coords = gav.split(":");
            if (coords.length != 3) {
                throw new ArgumentException("invalid gav url: " + gav);
            }
            artifactId = coords[1];
            idx = artifactId.indexOf('=');
            if (idx == -1) {
                name = artifactId;
            } else {
                name = artifactId.substring(idx + 1);
                artifactId = artifactId.substring(0, idx);
            }
            if (!names.add(name)) {
                throw new ArgumentException("duplicate name: " + name + "\nTry groupId:artifactId=othername:version in your url.");
            }
            artifact = new DefaultArtifact(coords[0], artifactId, "war", coords[2]);
            applications.add(new Application(session.gson, (DefaultArtifact) artifact.setFile(directory.join(name, "ROOT.war").toPath().toFile()),
              directory, maven(), session.console));
        }

    }

    public static boolean isArtifact(String url) {
        return url.startsWith("gav:");
    }

    public static FileNode gavFile(FileNode directory) {
        return directory.join("gav.url");
    }

    @Override
    public List<DefaultArtifact> scanWars() throws IOException {
        return applications.artifacts();
    }

    @Override
    public String getDefaultBuildCommand() {
        return "echo nothing to do";
    }

    public void populateDirectory(Console console) throws IOException {
        FileNode directory;

        directory = getDirectory();
        console.verbose.println(directory.getAbsolute());
        directory.checkDirectory();
        getGavFile().writeString(url);

        for (Application application : applications.applications()) {
            application.currentFile().getParent().mkdir();
        }
        refresh(console);
    }

    @Override
    public void prepareRefresh(Console console) throws IOException {
        for (Application application : applications.applications()) {
            refreshWar(console, application);
        }

        //TODO: Do the changes stuff here
    }

    @Override
    public boolean updateAvailable() {
        try {
            for (Application application : applications.applications()) {
                if (application.updateAvalable()) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    @Override
    public void executeRefresh(Console console) throws IOException {

        for (Application application : applications.applications()) {

            if (application.updateAvalable()) {
                application.update();
            }
        }
    }

    @Override
    public void restoreFromBackup(Console console) throws IOException {
        try {
            for (Application application : applications.applications()) {
                application.restore();
            }
        } catch (MoveException e) {
            throw new IOException("TODO", e);
        }
    }

    @Override
    public Map<String, FileNode> hosts() {
        Map<String, FileNode> result;
        FileNode dir;

        result = new LinkedHashMap<>();
        for (Application application : applications.applications()) {
            dir = application.currentFile().getParent();
            result.put(dir.getName() + "." + getName(), dir.join("ROOT"));
        }

        return result;
    }

    private void refreshWar(Console console, Application application) throws IOException {
        WarFile candidate;
        Changes changes;

        candidate = sourceFor(application).resolve();
        if (candidate.equals(application.currentWarFile())) {
            return;
        }

        if (candidate.equals(application.futureWarFile())) {
            return;
        }

        application.replaceFutureWarFile(candidate);
        changes = application.changes(shared(), session.users);
        console.verbose.println("Update for " + application.artifactId() + " prepared.");
        for (Change change : changes) {
            console.info.print(change.getUser());
            console.info.print(" : ");
            console.info.println(change.getMessage());
        }
    }

    public ArtifactSource sourceFor(Application application) throws IOException {
        ArtifactSource source;

        if ("@inbox".equals(application.artifact().getVersion())) {
            source = new Inbox(application.name(), getName(), session.home.join(Main.INBOX));
        } else if ("@overview".equals(application.artifact().getVersion())) {
            source = new Overview(Session.jdkHome(), session.home.getWorld());
        } else {
            source = new Maven(application.artifact(), maven());
        }
        return source;
    }

    private String getGavs() {
        return Strings.removeLeft(url, "gav:");
    }

    private FileNode getGavFile() {
        return gavFile(directory);
    }

    @Override
    //TODO
    public boolean isOverview() {
        return url.contains(":@overview");
    }

    @Override
    public Changes changes() throws IOException {
        return applications.changes(shared(), session.users);
    }
}
