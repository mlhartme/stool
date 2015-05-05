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
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.ModeException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.filter.Filter;
import net.oneandone.sushi.fs.svn.SvnNode;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SourceStage extends Stage {
    public static SourceStage forUrl(Session session, FileNode wrapper, FileNode directory, String url, StageConfiguration configuration)
            throws IOException {
        return new SourceStage(session, wrapper, directory, url, configuration);
    }
    public static SourceStage forLocal(Session session, FileNode wrapper, FileNode stage, StageConfiguration configuration)
            throws IOException {
        return forUrl(session, wrapper, stage, SvnNode.urlFromWorkspace(stage), configuration);
    }

    //--

    /** loaded on demand */
    private List<MavenProject> lazyWars;

    public SourceStage(Session session, FileNode wrapper, FileNode directory, String url, StageConfiguration configuration)
      throws ModeException {
        super(session, url, wrapper, directory, configuration);
    }

    @Override
    public String getDefaultBuildCommand() {
        return "mvn -B -U clean package";
    }

    @Override
    public boolean updateAvailable() {
        return false;
    }

    @Override
    public List<DefaultArtifact> scanWars() throws IOException {
        List<DefaultArtifact> result;
        DefaultArtifact artifact;

        result = new ArrayList<>();
        for (MavenProject project : wars()) {
            artifact = new DefaultArtifact(project.getGroupId(), project.getArtifactId(), "war", project.getVersion());
            artifact = (DefaultArtifact) artifact.setFile(warFile(project).toPath().toFile());
            result.add(artifact);
        }
        return result;
    }

    @Override
    public Map<String, FileNode> hosts() throws IOException {
        Map<String, FileNode> applications;

        applications = new LinkedHashMap<>();
        for (MavenProject project : wars()) {
            applications.put(project.getArtifactId() + "." + getName(), docroot(session.console.world, project));
        }
        return applications;
    }

    private List<MavenProject> wars() throws IOException {
        if (lazyWars == null) {
            lazyWars = loadWars(directory.join(config().pom));
        }
        return lazyWars;
    }

    private FileNode docroot(World world, MavenProject project) throws IOException {
        FileNode directory;
        List<FileNode> result;
        Filter filter;

        directory = world.file(project.getBasedir());
        filter = directory.getWorld().filter();
        // TODO: support web.xml-less apps that use annotations instead ...
        filter.include("target/*/WEB-INF/web.xml");
        filter.exclude("target/test-classes/**/*");
        result = (List) directory.find(filter);
        switch (result.size()) {
            case 0:
                throw new FileNotFoundException("No web.xml found. Did you build the project?");
            case 1:
                return result.get(0).getParent().getParent();
            default:
                throw new FileNotFoundException("web.xml ambiguous: " + result);
        }
    }
}

