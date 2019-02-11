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
package net.oneandone.stool.stage;

import net.oneandone.inline.Console;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.scm.Scm;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.filter.Filter;
import org.apache.maven.project.MavenProject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SourceProject extends Project {
    public static SourceProject forOrigin(Session session, String id, FileNode directory, String origin, StageConfiguration configuration) {
        return new SourceProject(session, id, directory, origin, configuration);
    }
    public static SourceProject forLocal(Session session, String id, FileNode stage, StageConfiguration configuration)
            throws IOException {
        return forOrigin(session, id, stage, Scm.checkoutUrl(stage), configuration);
    }

    //--

    /** loaded on demand */
    private List<MavenProject> lazyWars;

    public SourceProject(Session session, String id, FileNode directory, String origin, StageConfiguration configuration) {
        super(session, origin, id, directory, configuration);
    }

    @Override
    public List<String> faultProjects() throws IOException {
        List<String> result;

        result = new ArrayList<>();
        for (MavenProject project : wars()) {
            result.add("file:" + project.getFile().getAbsolutePath());
        }
        return result;
    }

    public boolean refreshPending(Console console) {
        // we can always try svn up
        // Not that I cannot check for the latest revision because this might be interactive, and I'd need user interaction ...
        return true;
    }

    @Override
    public int size() throws IOException {
        return wars().size();
    }

    public List<FileNode> artifacts() throws IOException {
        List<FileNode> result;
        FileNode basedir;
        Filter filter;

        filter = directory.getWorld().filter();
        filter.include("target/*.war");
        result = new ArrayList<>();
        for (MavenProject project : wars()) {
            basedir = directory.getWorld().file(project.getBasedir());
            result.addAll(basedir.find(filter));
        }
        return result;
    }

    @Override
    public Map<String, FileNode> vhosts() throws IOException {
        Map<String, FileNode> applications;

        applications = new LinkedHashMap<>();
        for (MavenProject project : wars()) {
            applications.put(project.getArtifactId(), docroot(stage.session.world, project));
        }
        return applications;
    }

    public List<MavenProject> wars() throws IOException {
        if (lazyWars == null) {
            lazyWars = loadWars(directory.join(stage.config().pom));
        }
        return lazyWars;
    }

    private FileNode docroot(World world, MavenProject project) throws IOException {
        FileNode directory;
        List<FileNode> result;
        Filter filter;

        directory = world.file(project.getBasedir());
        filter = directory.getWorld().filter();
        filter.include("target/*/WEB-INF");
        filter.predicate((node, b) -> node.isDirectory() && (node.join("lib").isDirectory() || node.join("classes").isDirectory()));
        filter.exclude("target/test-classes/**/*");
        result = directory.find(filter);
        switch (result.size()) {
            case 0:
                throw new IOException("No web application found. Did you build the project?");
            case 1:
                return result.get(0).getParent();
            default:
                throw new FileNotFoundException("web.xml ambiguous: " + result);
        }
    }
}

