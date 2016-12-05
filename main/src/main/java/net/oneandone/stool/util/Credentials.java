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
package net.oneandone.stool.util;

import net.oneandone.sushi.util.Strings;

public class Credentials {
    public final String username;
    public final String password;

    public Credentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String[] svnArguments() {
        return username == null ? Strings.NONE : new String[] {
                "--no-auth-cache",
                "--non-interactive", // to avoid password question if svnpassword is wrong
                "--username", username,
                "--password", password,
        };
    }

    public String stoolSvnArguments() {
        return username == null ? "" : "-svnuser " + username + " -svnpassword " + password;
    }

}
