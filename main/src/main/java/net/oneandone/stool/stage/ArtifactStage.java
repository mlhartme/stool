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
import net.oneandone.stool.stage.artifact.FileLocator;
import net.oneandone.stool.stage.artifact.GavLocator;
import net.oneandone.stool.stage.artifact.Locator;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.MoveException;
import net.oneandone.sushi.fs.file.FileNode;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.io.IOException;
import java.net.URISyntaxException;
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

        String name;
        Locator locator;
        Set<String> names;
        int idx;

        applications = new Applications();
        names = new HashSet<>();
        for (String part : url.split(",")) {
            idx = part.indexOf('=');
            if (idx == -1) {
                locator = locator(part);
                name = locator.defaultName();
            } else {
                name = part.substring(idx + 1);
                locator = locator(part.substring(0, idx));
            }
            if (!names.add(name)) {
                throw new ArgumentException("duplicate name: " + name);
            }
            applications.add(new Application(session.gson, name, locator, directory, session.console));
        }
    }

    private Locator locator(String locator) throws IOException {
        String[] coords;
        String artifactId;
        String version;

        if (locator.startsWith("gav:")) {
            coords = locator.substring(4).split(":");
            if (coords.length != 3) {
                throw new ArgumentException("invalid gav url: " + locator);
            }
            artifactId = coords[1];
            version = coords[2];
            return new GavLocator(maven(), new DefaultArtifact(coords[0], artifactId, "war", version));
        } else if (locator.startsWith("file:")) {
            try {
                return new FileLocator((FileNode) session.console.world.node(locator));
            } catch (URISyntaxException e) {
                throw new ArgumentException(locator + ": invalid file locator: " + e.getMessage(), e);
            }
        } else {
            throw new ArgumentException("unknown locator: " + locator);
        }
    }

    public static boolean isArtifact(String url) {
        return url.startsWith("gav:") || url.startsWith("file:");
    }

    public static FileNode urlFile(FileNode directory) {
        return directory.join("stage.url");
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
            if (!application.refreshFuture(session, shared())) {
                throw new IOException("application not found: " + application.location);
            }
        }
        executeRefresh(console);
    }

    @Override
    public boolean refreshPending(Console console) throws IOException {
        boolean result;

        result = false;
        for (Application application : applications.applications()) {
            if (application.refreshFuture(session, shared())) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public boolean updateAvailable() {
        for (Application application : applications.applications()) {
            if (application.updateAvailable()) {
                return true;
            }
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

    private FileNode getGavFile() {
        return urlFile(directory);
    }

    @Override
    public Changes changes() {
        return applications.changes(shared(), session.users);
    }
}
