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

import net.oneandone.stool.users.Users;

import java.io.IOException;

public class SCMChangeCollector {
    public static Changes run(WarFile current, WarFile future, Users users, String svnurl) throws IOException {
        SCMChangeCollector changeCollector;
        long futureRev;
        long currentRev;

        currentRev = current.revision();
        futureRev = future.revision();

        changeCollector = new SCMChangeCollector(svnurl, currentRev + 1, futureRev, users);
        return changeCollector.collect();
    }

    private final long revisionA;
    private final long revisionB;
    private final String url;
    private final Users users;

    public SCMChangeCollector(String url, long revisionA, long revisionB, Users users) {
        this.revisionA = revisionA;
        this.revisionB = revisionB;
        this.url = url;
        this.users = users;
    }

    public Changes collect() {
        Changes changes;

        // TODO
        changes = new Changes();
        return changes;
    }

}
