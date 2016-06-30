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
package net.oneandone.stool.cli;

import net.oneandone.setenv.Setenv;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Select extends SessionCommand {
    private String stageName;

    public Select(Session session, String stageName) {
        super(session, Mode.NONE);
        this.stageName = stageName;
    }

    @Override
    public void doRun() throws Exception {
        FileNode dir;

        if (stageName == null) {
            console.info.println("currently selected: "
                    + (session.getSelectedStageId() == null ? "none" : session.getSelectedStageId()));
            return;
        }
        if ("none".equals(stageName)) {
            if (session.getSelectedStageId() == null) {
                console.info.println("already selected: none");
            } else {
                console.verbose.println("selecting none");
                Setenv.get().cd(world.getWorking().getParent().getAbsolute());
            }
            return;
        }
        dir = session.lookup(stageName);
        if (dir == null) {
            throw new IOException("No such stage: " + stageName + suggestion());
        }
        console.verbose.println("selecting stage " + stageName);
        Setenv.get().cd(dir.getAbsolute());
    }

    private String suggestion() throws IOException {
        List<String> candidates;

        candidates = candidates(session.stageNames(), stageName);
        switch (candidates.size()) {
            case 0:
                return "";
            case 1:
                return "\nDid you mean " + candidates.get(0) + "?";
            default:
                return "\nDid you mean one of " + candidates + "?";
        }
    }

    public static List<String> candidates(List<String> names, String search) {
        String reduced;
        List<String> result;

        result = new ArrayList<>();
        reduced = reduce(search);
        for (String name : names) {
            if (reduce(name).contains(reduced)) {
                result.add(name);
            }
        }
        return result;
    }

    private static String reduce(String name) {
        StringBuilder builder;
        char c;

        builder = new StringBuilder();
        for (int i = 0, max = name.length(); i < max; i++) {
            c = name.charAt(i);
            if (c >= '0' && c <= '9') {
                builder.append(c);
            } else if (c >= 'a' && c <= 'z') {
                builder.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }
}