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

import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.stage.artifact.Application;
import net.oneandone.stool.stage.artifact.Applications;
import net.oneandone.stool.stage.artifact.Changes;
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
import java.util.Map;
import java.util.Set;

/**
 * Run WAR-Artifacts without sources (instead of projects which are packaged as WARs).
 */
public class ArtifactStage extends Stage {
    private final Applications applications;

    public ArtifactStage(Session session, String url, FileNode backstage, FileNode directory, StageConfiguration configuration)
      throws IOException {
        super(session, url, backstage, directory, configuration);

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
    public int size() throws IOException {
        return applications.size();
    }

    @Override
    public String getDefaultBuildCommand() {
        return "echo nothing to build";
    }

    public void populateDirectory(Console console) throws IOException {
        FileNode directory;

        directory = getDirectory();
        console.verbose.println(directory.getAbsolute());
        directory.checkDirectory();
        getGavFile().writeString(url);

        for (Application application : applications.applications()) {
            application.populate();
        }
        refreshPending(console);
        executeRefresh(console);
    }

    @Override
    public boolean refreshPending(Console console) throws IOException {
        boolean result;

        result = false;
        for (Application application : applications.applications()) {
            if (application.refreshWar(session, shared())) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public boolean updateAvailable() {
        try {
            for (Application application : applications.applications()) {
                if (application.updateAvailable()) {
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
            if (application.updateAvailable()) {
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
    public Map<String, FileNode> vhosts() {
        Map<String, FileNode> result;
        FileNode base;

        result = new LinkedHashMap<>();
        for (Application application : applications.applications()) {
            base = application.base();
            result.put(base.getName(), base.join("ROOT"));
        }

        return result;
    }

    private String getGavs() {
        return Strings.removeLeft(url, "gav:");
    }

    private FileNode getGavFile() {
        return gavFile(directory);
    }

    @Override
    public Changes changes() {
        return applications.changes(shared(), session.users);
    }
}
