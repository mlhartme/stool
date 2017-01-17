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
package net.oneandone.stool.dashboard.config;

import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.Properties;

public class DashboardProperties {
    public static DashboardProperties load(FileNode home) throws IOException {
        FileNode props;
        Properties p;

        props = home.join("system/dashboard.properties");
        if (!props.exists()) {
            return new DashboardProperties(null, null, null);
        }
        p = props.readProperties();
        return new DashboardProperties(p.getProperty("sso"), p.getProperty("svnuser"), p.getProperty("svnpassword"));
    }

    public final String sso;

    public final String svnuser;
    public final String svnpassword;

    public DashboardProperties(String sso, String svnuser, String svnpassword) {
        this.sso = sso;
        this.svnuser = svnuser;
        this.svnpassword = svnpassword;
    }
}
