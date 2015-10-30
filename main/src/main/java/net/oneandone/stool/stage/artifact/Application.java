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

    private WarFile currentWarFile;
    private WarFile futureWarFile;
    private WarFile backupWarFile;

    public Application(Gson gson, DefaultArtifact artifact, FileNode stageDirectory, Maven maven, Console console) {
        this.gson = gson;
        this.artifact = artifact;
        this.stageDirectory = stageDirectory;
        this.maven = maven;
        this.console = console;
    }

    public String artifactId() {
        return artifact.getArtifactId();
    }

    private String name() {
        return artifact.getArtifactId();
    }

    public DefaultArtifact artifact() throws IOException {
        String version;

        try {
            if (artifact.getVersion().equals("@latest")) {
                version = maven.latestRelease(artifact);
                artifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "war", version);
            }
        } catch (VersionRangeResolutionException e) {
            throw new IOException(e);
        }
        return artifact;
    }

    private WarFile currentWarFile() {
        if (currentWarFile == null) {
            currentWarFile = new WarFile(currentFile());
        }
        return currentWarFile;
    }

    private WarFile futureWarFile() throws IOException {
        if (futureWarFile == null) {
            futureWarFile = new WarFile(futureFile());
        }
        return futureWarFile;
    }

    private WarFile backupWarFile() {
        if (backupWarFile == null) {
            backupWarFile = new WarFile(backupFile());
        }
        return backupWarFile;
    }

    public FileNode currentFile() {
        return stageDirectory.join(artifactId(), "ROOT.war");
    }

    private FileNode futureFile() throws MkdirException {
        return (FileNode) stageDirectory.join(".refresh").mkdirsOpt().join(name() + ".war.next");
    }

    private FileNode backupFile() {
        return stageDirectory.join(".refresh").join(name() + ".war.backup");
    }

    public void update() throws IOException {
        backup();
        currentWarFile = futureWarFile().saveTo(currentFile());
        console.verbose.println("Update for " + artifactId() + " executed.");
        currentFile().getParent().join("ROOT").deleteTreeOpt();
    }

    public void restore() throws IOException {
        if (backupFile().exists()) {
            console.info.println("Restoring backup of  " + artifactId());
            currentWarFile = backupWarFile().saveTo(currentFile());
            console.info.println("Restored.");
        } else {
            console.info.println("No backup available for " + artifactId());
        }
    }


    private Changes changes(FileNode shared, Users users) throws IOException {
        FileNode file;
        String svnurl;
        Changes changes;

        if (artifact.getVersion().equals("@dashboard")) {
            return new Changes();
        }
        if (!futureWarFile().exists() || !currentWarFile().exists()) {
            return new Changes();
        }
        file = shared.join("changes").join(futureFile().md5() + ".changes");
        if (file.exists()) {
            try (Reader src = file.createReader()) {
                return gson.fromJson(src, Changes.class);
            }
        }
        svnurl = pom().getScm().getUrl();
        if (svnurl.contains("tags")) {
            changes = new XMLChangeCollector(currentWarFile(), futureWarFile()).collect();
        } else {
            changes = SCMChangeCollector.run(currentWarFile(), futureWarFile(), users, svnurl);
        }
        Files.createStoolDirectoryOpt(console.verbose, file.getParent());
        Files.stoolFile(file.writeString(gson.toJson(changes)));
        return changes;
    }

    private MavenProject pom() throws IOException {
        try {
            return maven.loadPom(artifact());
        } catch (RepositoryException | ProjectBuildingException e) {
            throw new IOException("Cannot load projects pom", e);
        }
    }

    private void backup() throws IOException {
        if (currentFile().exists()) {
            currentFile().copy(backupFile());
            console.info.println("Backup for " + artifactId() + " created.");
        }
    }

    public boolean updateAvailable() throws IOException {
        if (!futureFile().exists()) {
            return false;
        }

        if (!currentFile().exists()) {
            return true;
        }

        return true;
    }

    //--

    public boolean refreshWar(Session session, FileNode shared) throws IOException {
        final DefaultArtifact artifact;
        WarFile candidate;
        Changes changes;

        artifact = artifact();
        try {
            candidate = new WarFile(maven.resolve(artifact));
        } catch (ArtifactResolutionException e) {
            throw new FileNotFoundException("Artifact " + artifact + " not found.");
        }
        if (candidate.equals(currentWarFile())) {
            return false;
        }
        if (candidate.equals(futureWarFile())) {
            return false;
        }

        futureWarFile = candidate.saveTo(futureFile());

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
}
