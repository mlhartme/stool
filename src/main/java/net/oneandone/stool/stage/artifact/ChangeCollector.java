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

import com.oneandone.sales.tools.devreg.model.DeveloperNotFound;
import com.oneandone.sales.tools.devreg.model.Ldap;

import javax.naming.NamingException;
import java.io.IOException;

public class ChangeCollector {
    private final WarFile current;
    private final WarFile future;
    private final Ldap ldap;

    public ChangeCollector(WarFile current, WarFile future, Ldap ldap) {
        this.current = current;
        this.future = future;
        this.ldap = ldap;
    }
    public Changes withSCM(String svnurl) throws NoChangesAvailableException {
        SCMChangeCollector changeCollector;
        long futureRev;
        long currentRev;

        try {
            currentRev = current.revision();
            futureRev = future.revision();

            changeCollector = new SCMChangeCollector(svnurl, currentRev + 1, futureRev, ldap);
            return changeCollector.collect();
        } catch (IOException | DeveloperNotFound | NamingException e) {
            throw new NoChangesAvailableException(e);
        }
    }

    public Changes withXMLChanges() throws NoChangesAvailableException {
        try {
            return new XMLChangeCollector(current, future).collect();
        } catch (IOException e) {
            throw new NoChangesAvailableException(e);
        }
    }
}
