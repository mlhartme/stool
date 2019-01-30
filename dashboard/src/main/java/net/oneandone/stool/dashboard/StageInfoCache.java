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
package net.oneandone.stool.dashboard;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.Users;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StageInfoCache {
    private final Collection<StageInfo> stages = new ArrayList<>();
    private long lastCacheRenew;

    public StageInfoCache() {
        lastCacheRenew = 0L;
    }

    public Collection<StageInfo> get(FileNode logs, Session session, Users users) throws IOException {
        List<Stage> lst;

        if (System.currentTimeMillis() - lastCacheRenew > 4000) {
            stages.clear();
            session.wipeStaleBackstages();
            session.updatePool();
            lst = session.listWithoutSystem();
            Collections.sort(lst, new Comparator<Stage>() {
                @Override
                public int compare(Stage left, Stage right) {
                    boolean lr;
                    boolean rr;

                    lr = left.config().expire.isReserved();
                    rr = right.config().expire.isReserved();
                    if (lr == rr) {
                        return left.getName().compareTo(right.getName());
                    } else {
                        return lr ? -1 : 1;
                    }
                }
            });
            for (Stage stage : lst) {
                try {
                    stages.add(StageInfo.fromStage(logs, stage, users));
                } catch (NamingException e) {
                    session.reportException("StageInfo.fromStage", e);
                    // fall-through
                }
            }
            lastCacheRenew = System.currentTimeMillis();
        }
        return stages;
    }
}
