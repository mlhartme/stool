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
package com.oneandone.sales.tools.stool.overview;

import com.oneandone.sales.tools.stool.EnumerationFailed;
import com.oneandone.sales.tools.stool.Overview;
import com.oneandone.sales.tools.stool.stage.Stage;
import com.oneandone.sales.tools.stool.util.Predicate;
import com.oneandone.sales.tools.stool.util.Session;
import com.oneandone.sales.tools.devreg.model.DeveloperNotFound;
import org.xml.sax.SAXException;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class StageGatherer {
    public static List<StageInfo> get(Session session, Developers developers)
            throws IOException, SAXException, NamingException, DeveloperNotFound, EnumerationFailed {
        List<StageInfo> stageInfos;
        stageInfos = new ArrayList<>();
        session.getProcesses(true);

        List<Stage> stages = getStages(session);

        for (Stage stage : stages) {
            stageInfos.add(StageInfo.fromStage(stage, developers));
        }
        return stageInfos;
    }

    private static List<Stage> getStages(Session session) throws IOException, EnumerationFailed {
        return doList(session, new Predicate() {
            @Override
            public boolean matches(com.oneandone.sales.tools.stool.stage.Stage stage) {
                return !stage.getName().equals(Overview.OVERVIEW_NAME);
            }
        });
    }

    public static StageInfo get(String name, Session session, Developers developers)
            throws IOException, SAXException, NamingException, DeveloperNotFound, EnumerationFailed {
        List<Stage> stages = getStages(name, session);
        session.getProcesses(true);
        return StageInfo.fromStage(stages.get(0), developers);
    }

    public static List<Stage> getStages(final String name, Session session) throws IOException, EnumerationFailed {
        return doList(session, new Predicate() {
            @Override
            public boolean matches(com.oneandone.sales.tools.stool.stage.Stage stage) {
                return stage.getName().equals(name);
            }
        });
    }

    public static List<Stage> getAllStages(Session session) throws IOException, EnumerationFailed {
        return doList(session, new Predicate() {
            @Override
            public boolean matches(com.oneandone.sales.tools.stool.stage.Stage stage) {
                return !stage.isOverview();
            }
        });
    }

    private static List<Stage> doList(Session session, Predicate predicate) throws IOException, EnumerationFailed {
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
