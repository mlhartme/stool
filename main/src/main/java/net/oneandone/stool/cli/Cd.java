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
package net.oneandone.stool.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Project;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.filter.Filter;
import net.oneandone.sushi.fs.filter.Predicate;

import java.util.List;

public class Cd extends StageCommand {
    private String target;

    public Cd(Session session) {
        super(false, session, Mode.NONE, Mode.SHARED, Mode.NONE);
        this.target = null;
    }

    public void setTarget(String str) {
        this.target = str;
    }

    @Override
    public void doMain(Project project) throws Exception {
        FileNode node;
        List<FileNode> lst;
        Filter filter;
        StringBuilder message;

        if (target == null) {
            node = project.getDirectory();
        } else if ("backstage".equals(target)) {
            node = project.getBackstage();
        } else {
            filter = world.filter();
            filter.includeAll();
            filter.maxDepth(2);
            filter.predicate(Predicate.DIRECTORY);
            lst = project.getBackstage().find(filter);
            node = null;
            message = new StringBuilder();
            for (FileNode candidate : lst) {
                if (candidate.getName().equals(target)) {
                    node = candidate;
                    break;
                }
                message.append(", ");
                message.append(candidate.getName());
            }
            if (node == null) {
                throw new ArgumentException("unknown target: " + target + ". Choose one of backstage" + message + " or leave empty.");
            }
        }
        session.cd(node);
    }
}
