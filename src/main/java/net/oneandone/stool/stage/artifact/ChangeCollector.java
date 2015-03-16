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

import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.users.Users;

import javax.naming.NamingException;
import java.io.IOException;

public class ChangeCollector {
    private final WarFile current;
    private final WarFile future;
    private final Users users;

    public ChangeCollector(WarFile current, WarFile future, Users users) {
        this.current = current;
        this.future = future;
        this.users = users;
    }

    public Changes withSCM(String svnurl) throws IOException {
        SCMChangeCollector changeCollector;
        long futureRev;
        long currentRev;

        currentRev = current.revision();
        futureRev = future.revision();

        changeCollector = new SCMChangeCollector(svnurl, currentRev + 1, futureRev, users);
        try {
            return changeCollector.collect();
        } catch (UserNotFound | NamingException e) {
            throw new IOException("error collecting changelog: " + e.getMessage(), e);
        }
    }

    public Changes withXMLChanges() throws IOException {
        return new XMLChangeCollector(current, future).collect();
    }
}
