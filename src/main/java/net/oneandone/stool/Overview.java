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
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Overview {
    public static final String OVERVIEW_NAME = "overview";
    private final Session session;

    public Overview(Session session) {
        this.session = session;
    }

    /** @param user is intentionally not session.user */
    public static void createOverview(Session session, String user) throws IOException {
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
        tomcatOpts += "-Doverview.stool.home=" + session.home.getAbsolute() + " -Doverview.user.name=" + user;
        create.remaining("tomcat.opts=" + tomcatOpts);
        create.remaining("until=reserved");
        create.remaining("tomcat.env=" + environment());
        try {
            create.doInvoke();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    // make sure the overview sees all environment variables, because the build command expects this environment
    private static String environment() {
        List<String> keys;

        keys = new ArrayList<>(System.getenv().keySet());
        Collections.sort(keys);
        return Separator.COMMA.join(keys);
    }

    private static FileNode overviewDirectory(Session session) {
        return session.home.join(OVERVIEW_NAME);
    }

}
