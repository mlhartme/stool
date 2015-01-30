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

import net.oneandone.stool.devreg.DeveloperNotFound;
import net.oneandone.stool.devreg.Ldap;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNClientManager;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.Collection;

public class SCMChangeCollector {
    private final long revisionA;
    private final long revisionB;
    private final String url;
    private Ldap ldap;

    public SCMChangeCollector(String url, long revisionA, long revisionB, Ldap ldap) {
        this.revisionA = revisionA;
        this.revisionB = revisionB;
        this.url = url;
        this.ldap = ldap;
    }

    public Changes collect() throws IOException, NamingException, DeveloperNotFound {

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
                    change = new Change(logEntry.getRevision(), ldap.developerByLogin(logEntry.getAuthor()),
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
