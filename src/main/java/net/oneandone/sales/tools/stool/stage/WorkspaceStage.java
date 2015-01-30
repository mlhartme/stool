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
package net.oneandone.sales.tools.stool.stage;

import net.oneandone.sales.tools.stool.configuration.StageConfiguration;
import net.oneandone.sales.tools.stool.util.Files;
import net.oneandone.sales.tools.stool.util.ServerXml;
import net.oneandone.sales.tools.stool.util.Session;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.ModeException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WorkspaceStage extends Stage {
    public WorkspaceStage(Session session, FileNode wrapper, FileNode directory, String url, StageConfiguration configuration)
      throws ModeException {
        super(session, url, wrapper, directory, configuration);
    }

    @Override
    public boolean updateAvailable() {
        return false;
    }
    public List<DefaultArtifact> scanWars() throws IOException {
        List<DefaultArtifact> result;
        DefaultArtifact artifact;

        result = new ArrayList<>();
        for (MavenProject project : loadWars(directory.join("workspace.xml"))) {
            artifact = new DefaultArtifact(project.getGroupId(), project.getArtifactId(), "war", project.getVersion());
            artifact = (DefaultArtifact) artifact.setFile(warFile(project).toPath().toFile());
            result.add(artifact);
        }
        return result;
    }


    @Override
    public String getDefaultBuildCommand() {
        return "pwsraw -U build";
    }

    @Override
    public void start(Console console) throws Exception {
        FileNode applogs;

        FileNode editorLocations = directory.join("tomcat/editor/WEB-INF/editor-locations.xml");
        if (editorLocations.exists()) {
            editorLocations.writeString(editorLocations.readString().replace("8080", Integer.toString(configuration.ports.tomcatHttp())));
            Files.stoolFile(editorLocations);
        }

        ServerXml serverXml = ServerXml.load(directory.join("tomcat/server.xml"));
        serverXml.connectors(configuration.ports, keystore());
        serverXml.contexts(configuration.mode, configuration.cookies, configuration.ports);

        applogs = shared().join("applogs");
        Files.stoolDirectory(applogs.mkdirOpt());
        serverXml.applogs(directory.join("tomcat/target/tomcat/applogs").getAbsolute() + "/", applogs.getAbsolute() + "/");

        startTomcat(serverXml, console);
    }

    @Override
    public void stop(Console console) throws IOException {
        stopTomcat(console);
    }

    @Override
    protected String getAppName() {
        int start = url.lastIndexOf('/');
        return url.substring(start + 1);
    }

    @Override
    public void refresh(Console console, boolean forcePrepare) throws ModeException, Failure {
        launcher("pwsraw", "up").exec(console.info);
    }
}

