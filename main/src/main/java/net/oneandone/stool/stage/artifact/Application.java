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
package net.oneandone.stool.stage.artifact;

import com.google.gson.Gson;
import net.oneandone.maven.embedded.Maven;
import net.oneandone.stool.users.Users;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeResolutionException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;

public class Application {
    private final Gson gson;
    private final FileNode stageDirectory;
    private DefaultArtifact artifact;
    private final Maven maven;
    private final Console console;

    private WarFile backupWarFile;
    private WarFile currentWarFile;
    private WarFile futureWarFile;

    public Application(Gson gson, DefaultArtifact artifact, FileNode stageDirectory, Maven maven, Console console) {
        this.gson = gson;
        this.artifact = artifact;
        this.stageDirectory = stageDirectory;
        this.maven = maven;
        this.console = console;
        this.backupWarFile = new WarFile(refresh().join(name() + ".war.backup"));
        this.currentWarFile = new WarFile(base().join("ROOT.war"));
        this.futureWarFile = new WarFile(refresh().join(name() + ".war.next"));

    }

    public String artifactId() {
        return artifact.getArtifactId();
    }

    public void populate() throws MkdirException {
        base().mkdir();
        refresh().mkdir();
    }

    private FileNode refresh() {
        return stageDirectory.join(".refresh");
    }

    public FileNode base() {
        return stageDirectory.join(artifactId());
    }

    public boolean refreshWar(Session session, FileNode shared) throws IOException {
        WarFile candidate;
        Changes changes;

        updateArtifact();
        try {
            candidate = new WarFile(maven.resolve(artifact));
        } catch (ArtifactResolutionException e) {
            throw new FileNotFoundException("Artifact " + artifact + " not found.");
        }
        if (candidate.equals(currentWarFile)) {
            return false;
        }
        if (candidate.equals(futureWarFile)) {
            return false;
        }

        futureWarFile = candidate.saveTo(futureWarFile.file());

        try {
            changes = changes(shared, session.users);
        } catch (IOException e) {
            // TODO
            session.reportException("application.changes", e);
            changes = new Changes();
        }
        session.console.verbose.println("Update for " + artifactId() + " prepared.");
        for (Change change : changes) {
            console.info.print(change.getUser());
            console.info.print(" : ");
            console.info.println(change.getMessage());
        }
        return true;
    }

    public boolean updateAvailable() throws IOException {
        if (!futureWarFile.exists()) {
            return false;
        }

        if (!currentWarFile.exists()) {
            return true;
        }

        return true;
    }

    public void update() throws IOException {
        backup();
        currentWarFile = futureWarFile.saveTo(currentWarFile.file());
        console.verbose.println("Update for " + artifactId() + " executed.");
        currentWarFile.file().getParent().join("ROOT").deleteTreeOpt();
    }

    public void restore() throws IOException {
        if (backupWarFile.exists()) {
            console.info.println("Restoring backup of  " + artifactId());
            currentWarFile = backupWarFile.saveTo(currentWarFile.file());
            console.info.println("Restored.");
        } else {
            console.info.println("No backup available for " + artifactId());
        }
    }

    //--

    private Changes changes(FileNode shared, Users users) throws IOException {
        FileNode file;
        String svnurl;
        Changes changes;

        if (artifact.getVersion().equals("@dashboard")) {
            return new Changes();
        }
        if (!futureWarFile.exists() || !currentWarFile.exists()) {
            return new Changes();
        }
        file = shared.join("changes").join(futureWarFile.file().md5() + ".changes");
        if (file.exists()) {
            try (Reader src = file.createReader()) {
                return gson.fromJson(src, Changes.class);
            }
        }
        svnurl = pom().getScm().getUrl();
        if (svnurl.contains("tags")) {
            changes = new XMLChangeCollector(currentWarFile, futureWarFile).collect();
        } else {
            changes = SCMChangeCollector.run(currentWarFile, futureWarFile, users, svnurl);
        }
        Files.createStoolDirectoryOpt(console.verbose, file.getParent());
        Files.stoolFile(file.writeString(gson.toJson(changes)));
        return changes;
    }

    private MavenProject pom() throws IOException {
        try {
            updateArtifact();
            return maven.loadPom(artifact);
        } catch (RepositoryException | ProjectBuildingException e) {
            throw new IOException("Cannot load projects pom", e);
        }
    }

    private void backup() throws IOException {
        if (currentWarFile.exists()) {
            currentWarFile.file().copy(backupWarFile.file());
            console.info.println("Backup for " + artifactId() + " created.");
        }
    }

    //--

    private String name() {
        return artifact.getArtifactId();
    }

    private void updateArtifact() throws IOException {
        String version;

        if (artifact.getVersion().equals("@latest")) {
            try {
                version = maven.latestRelease(artifact);
            } catch (VersionRangeResolutionException e) {
                throw new IOException(e);
            }
            artifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "war", version);
        }
    }
}
