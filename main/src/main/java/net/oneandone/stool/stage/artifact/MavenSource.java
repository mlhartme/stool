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

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import java.io.FileNotFoundException;
import java.io.IOException;

public class MavenSource implements ArtifactSource {

    private final DefaultArtifact artifact;
    private final net.oneandone.maven.embedded.Maven maven;

    public MavenSource(DefaultArtifact artifact, net.oneandone.maven.embedded.Maven maven) {
        this.artifact = artifact;
        this.maven = maven;
    }

    @Override
    public WarFile resolve() throws IOException {
        try {
            return new WarFile(maven.resolve(artifact));
        } catch (ArtifactResolutionException e) {
            throw new FileNotFoundException("Artifact " + artifact + " not found.");
        }
    }
}
