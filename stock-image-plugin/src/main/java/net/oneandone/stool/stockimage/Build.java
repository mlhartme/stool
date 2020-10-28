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
package net.oneandone.stool.stockimage;

import net.oneandone.stool.docker.Daemon;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.http.StatusException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a Docker image and pushes it.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.NONE, threadSafe = true)
public class Build extends AbstractMojo {
    private final World world;

    /** Don't use Docker cache */
    @Parameter(defaultValue = "false")
    private final boolean noCache;

    @Parameter(property = "docker.repository",
            defaultValue = "contargo.server.lan/cisoops-public/${project.groupId}-${project.artifactId}") // TODO
    private final String repository;

    /**
     * Specifies the artifact to add to the Docker context.
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.war")
    private final String artifact;

    /** Explicit comment to add to image */
    @Parameter(defaultValue = "")
    private final String comment;

    // TODO
    private final Map<String, String> explicitArguments;

    public Build() throws IOException, MojoFailureException {
        this(World.create());
    }

    public Build(World world) throws MojoFailureException {
        this.world = world;
        this.noCache = false;
        this.repository = "";
        this.artifact = null;
        this.comment = "";
        this.explicitArguments = argument(new ArrayList()); // TODO
    }

    private static Map<String, String> argument(List<String> args) throws MojoFailureException {
        int idx;
        Map<String, String> result;

        result = new HashMap<>();
        for (String arg : args) {
            idx = arg.indexOf('=');
            if (idx == -1) {
                throw new MojoFailureException("invalid argument: <key>=<value> expected, got " + arg);
            }
            result.put(arg.substring(0, idx), arg.substring(idx + 1));
        }
        return result;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            build();
        } catch (IOException e) {
            throw new MojoExecutionException("io error: " + e.getMessage(), e);
        }
    }
    public void build() throws IOException, MojoFailureException {
        Source source;

        source = new Source(getLog(), world.file(artifact).checkFile());
        try (Daemon daemon = Daemon.create()) {
            source.build(daemon, repository, comment, noCache, explicitArguments);
        } catch (StatusException e) {
            throw new IOException(e.getResource() + ": " + e.getStatusLine(), e);
        }
    }

}
