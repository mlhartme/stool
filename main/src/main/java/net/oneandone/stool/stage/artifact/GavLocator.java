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
package net.oneandone.stool.stage.artifact;

import net.oneandone.maven.embedded.Maven;
import org.apache.maven.project.ProjectBuildingException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeResolutionException;

import java.io.FileNotFoundException;
import java.io.IOException;

public class GavLocator extends Locator {
    private final Maven maven;
    private DefaultArtifact artifact;

    public GavLocator(Maven maven, DefaultArtifact artifact) {
        this.maven = maven;
        this.artifact = artifact;
    }

    public String defaultName() {
        return artifact.getArtifactId();
    }

    public WarFile resolve() throws IOException {
        String version;

        if (artifact.getVersion().equals("@latest")) {
            try {
                version = maven.latestRelease(artifact);
            } catch (VersionRangeResolutionException e) {
                throw new IOException(e);
            }
            artifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "war", version);
        }
        try {
            return new WarFile(maven.resolve(artifact));
        } catch (ArtifactResolutionException e) {
            throw (IOException) new FileNotFoundException("Artifact " + artifact + " not found: " + e.getMessage()).initCause(e);
        }
    }

    public String svnurl() throws IOException {
        try {
            return maven.loadPom(artifact).getScm().getUrl();
        } catch (ProjectBuildingException | RepositoryException e) {
            throw new IOException(e.getMessage(), e);
        }

    }

    public String toString() {
        return artifact.toString();
    }
}
