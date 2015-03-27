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
import net.oneandone.stool.Overview;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.users.Users;
import net.oneandone.stool.util.Predicate;
import net.oneandone.stool.util.Session;
import org.xml.sax.SAXException;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class StageGatherer {

    public static List<Stage> getAllStages(Session session) throws IOException, EnumerationFailed {
        return doList(session, new Predicate() {
            @Override
            public boolean matches(Stage stage) {
                return !stage.isOverview();
            }
        });
    }

    public static List<Stage> doList(Session session, Predicate predicate) throws IOException, EnumerationFailed {
        List<Stage> result;
        EnumerationFailed problems;

        problems = new EnumerationFailed();
        result = session.list(problems, predicate);
        if (!problems.empty()) {
            throw problems;
        }
        return result;
    }
}
