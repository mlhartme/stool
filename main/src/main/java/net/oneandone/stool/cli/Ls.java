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

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.Info;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Ls extends InfoCommand {
    private List<List<String>> lines;

    public Ls(Session session, String defaults) {
        super(session, defaults);
        lines = new ArrayList<>();
    }

    @Override
    public boolean doBefore(List<Stage> stages, int indent) {
        List<String> line;

        if (selected.isEmpty()) {
            selected.addAll(defaults(session.property("name"), Field.STATE, Field.LAST_MODIFIED_BY, Field.URL, Field.DIRECTORY));
        }
        header("stages");

        line = new ArrayList<>();
        lines.add(line);
        for (Info info : selected) {
            line.add('(' + info.infoName() + ')');
        }
        return true;
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        List<String> line;
        Map<Info, Object> status;

        status = Status.status(session, stage);
        line = new ArrayList<>();
        lines.add(line);
        for (Info info : selected) {
            line.add(Status.toString(status.get(info)).replace("\t", " "));
        }
    }

    @Override
    public void doAfter() throws IOException {
        int padStorage = 8;
        List<Integer> widths;
        boolean first;

        widths = widths();
        first = true;
        for (List<String> line : lines) {
            console.info.print("   ");
            for (int i = 0; i < widths.size(); i++) {
                console.info.print("  ");
                console.info.print(Strings.padRight(line.get(i), widths.get(i)));
            }
            console.info.println();
            if (first) {
                console.info.println();
                first = false;
            }
        }
        message("");
        header("storage");
        message("        mem: " + Strings.padLeft("~" + session.memUnreserved() + " mb free", padStorage));
        quota();
        message("");
    }

    private void quota() throws IOException {
        int global;

        global = session.configuration.quota;
        if (global == 0) {
            message(" disk: quota disabled");
        } else {
            message(" disk: " + session.quotaReserved() + "/" + global + " mb reserved");
        }
    }

    private List<Integer> widths() {
        List<Integer> result;
        int max;

        result = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            max = 0;
            for (List<String> line : lines) {
                max = Math.max(max, line.get(i).length());
            }
            result.add(max);
        }
        return result;
    }

    protected List<Stage> defaultSelected(EnumerationFailed problems) throws IOException {
        return all(problems);
    }
}
