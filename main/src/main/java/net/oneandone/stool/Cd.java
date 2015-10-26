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
package net.oneandone.stool;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.filter.Filter;
import net.oneandone.sushi.fs.filter.Predicate;

import java.util.List;

public class Cd extends StageCommand {
    private String target;

    @Remaining
    public void addTarget(String str) {
        if (target != null) {
            throw new ArgumentException("too many targets");
        }
        this.target = str;
    }

    public Cd(Session session) {
        super(session);
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        FileNode node;
        List<Node> lst;
        Filter filter;
        StringBuilder message;

        if (target == null) {
            node = stage.getDirectory();
        } else if ("backstage".equals(target)) {
            node = stage.getBackstage();
        } else {
            filter = console.world.filter();
            filter.includeAll();
            filter.maxDepth(2);
            filter.predicate(Predicate.DIRECTORY);
            lst = stage.shared().find(filter);
            node = null;
            message = new StringBuilder();
            for (Node candidate : lst) {
                if (candidate.getName().equals(target)) {
                    node = (FileNode) candidate;
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
