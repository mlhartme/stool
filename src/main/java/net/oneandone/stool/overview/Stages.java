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
package net.oneandone.stool.overview;

import net.oneandone.stool.EnumerationFailed;
import net.oneandone.stool.devreg.DeveloperNotFound;
import net.oneandone.stool.util.Session;
import org.xml.sax.SAXException;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.Collection;

public class Stages {

    private Collection<StageInfo> stages;
    private long lastCacheRenew;

    public Stages() {
        lastCacheRenew = 0L;
    }

    public Collection<StageInfo> load(Session session, Developers developers)
            throws IOException, SAXException, NamingException, DeveloperNotFound, EnumerationFailed {
        if (System.currentTimeMillis() - lastCacheRenew > 4000) {
            stages = StageGatherer.get(session, developers);
            lastCacheRenew = System.currentTimeMillis();
        }
        return stages;
    }
}
