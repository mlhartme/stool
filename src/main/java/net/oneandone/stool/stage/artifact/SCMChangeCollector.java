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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNClientManager;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.Collection;

public class SCMChangeCollector {
    public static Changes run(WarFile current, WarFile future, Users users, String svnurl) throws IOException {
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

    private final long revisionA;
    private final long revisionB;
    private final String url;
    private Users users;

    public SCMChangeCollector(String url, long revisionA, long revisionB, Users users) {
        this.revisionA = revisionA;
        this.revisionB = revisionB;
        this.url = url;
        this.users = users;
    }

    public Changes collect() throws IOException, NamingException, UserNotFound {

        SVNClientManager clientManager = SVNClientManager.newInstance();
        SVNRepository repository = null;

        try {
            Changes changes;
            repository = clientManager.createRepository(SVNURL.parseURIEncoded(url), true);
            changes = new Changes();
            Collection log = repository.log(new String[]{""}, null, revisionA, revisionB, true, true);
            for (Object entry : log) {
                if (entry instanceof SVNLogEntry) {
                    SVNLogEntry logEntry = (SVNLogEntry) entry;
                    Change change;
                    change = new Change(logEntry.getRevision(), users.byLogin(logEntry.getAuthor()).name,
                      logEntry.getMessage(), logEntry.getDate().getTime());
                    changes.add(change);
                }
            }
            return changes;
        } catch (SVNException e) {
            throw new IOException(e);
        } finally {
            if (repository != null) {
                repository.closeSession();
            }
            clientManager.dispose();
        }

    }

}
