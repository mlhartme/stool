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
import net.oneandone.sushi.cli.Value;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Open extends OpeningCommand {
    @Value(name = "stage", position = 1)
    private String stageName;

    public Open(Session session) {
        super(session);
    }

    @Override
    public void doOpening() throws Exception {
        FileNode backstage;
        Stage stage;

        try {
            backstage = session.backstages.join(stageName);
        } catch (IllegalArgumentException e) {
            // user specified an absolute path
            throw new IOException("No such stage: " + stageName + suggestion());
        }
        if (!backstage.isDirectory()) {
            throw new IOException("No such stage: " + stageName + suggestion());
        }
        console.verbose.println("open shell for stage " + stageName);
        if (session.getSelectedStageName() == null) {
            session.backupEnvironment();
        }
        stage = Stage.load(session, backstage);
        session.select(stage);
        session.cd(stage.getDirectory());
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
