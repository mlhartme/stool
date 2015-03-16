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
package net.oneandone.stool;

import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Overview {
    public static final String OVERVIEW_NAME = "overview";
    private final Session session;
    private Stage stage;

    public Overview(Session session) {
        this.session = session;
    }

    public static Overview initiate(Session session) throws IOException {
        Overview overview;

        overview = new Overview(session);
        overview.stage = overview.loadOverview();
        return overview;
    }

    public static void createOverview(Session session) throws IOException {
        Create create;
        String url;
        String tomcatOpts;
        StageConfiguration stageConfiguration;

        stageConfiguration = session.createStageConfiguration("");
        url = "gav:overview:overview:@overview";
        create = new Create(session, true, OVERVIEW_NAME, url, overviewDirectory(session), stageConfiguration);
        tomcatOpts = session.createStageConfiguration(url).tomcatOpts;
        if (!tomcatOpts.isEmpty()) {
            tomcatOpts += " ";
        }
        tomcatOpts += "-Doverview.stool.home=" + session.home.getAbsolute() + " -Doverview.user.name=" + session.user;
        create.remaining("tomcat.opts=" + tomcatOpts);
        try {
            create.doInvoke();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public Stage stage() {
        return stage;
    }

    /**
     * Starts the overview
     */
    public void start() throws IOException {
        try {
            if (stage.state() == Stage.State.DOWN) {
                new Start(session, false, false).doInvoke(stage);
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public void refresh() throws Exception {
        session.console.verbose.println("Refreshing " + OVERVIEW_NAME);
        new Refresh(session).doInvoke(stage);
    }

    private Stage loadOverview() throws IOException {
        return Stage.load(session, overviewWrapper(session), overviewDirectory(session));
    }

    private static FileNode overviewWrapper(Session session) {
        return session.wrappers.join(OVERVIEW_NAME);
    }

    private static FileNode overviewDirectory(Session session) {
        return session.home.join(OVERVIEW_NAME);
    }

    public void stop() throws IOException {
        try {
            new Stop(session).doInvoke(stage);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
