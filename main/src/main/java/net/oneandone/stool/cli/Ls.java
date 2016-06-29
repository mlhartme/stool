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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Ls extends StageCommand {
    private List<Status.Field> selected;
    private List<List<String>> lines;

    public Ls(Session session) {
        super(session, Mode.NONE, Mode.SHARED, Mode.NONE);
        selected = new ArrayList<>();
        lines = new ArrayList<>();
    }

    public void field(String str) {
        try {
            selected.add(Status.Field.valueOf(str.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new ArgumentException(str + ": no such status field, choose one of " + Arrays.asList(Status.Field.values()));
        }
    }


    @Override
    public boolean doBefore(List<Stage> stages, int indent) {
        List<String> line;

        if (selected.isEmpty()) {
            selected.add(Status.Field.NAME);
            selected.add(Status.Field.STATE);
            selected.add(Status.Field.OWNER);
            selected.add(Status.Field.URL);
        }
        header("stages");

        line = new ArrayList<>();
        lines.add(line);
        for (Status.Field field : selected) {
            line.add('(' + field.toString().toLowerCase() + ')');
        }
        return true;
    }

    @Override
    public void doRun(Stage stage) throws Exception {
        List<String> line;
        Map<Status.Field, Object> status;

        status = Status.status(processes(), stage);
        line = new ArrayList<>();
        lines.add(line);
        for (Status.Field field : selected) {
            line.add(Status.toString(status.get(field)).replace("\t", " "));
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
        message("   mem free: " + Strings.padLeft("~" + session.memUnreserved() + " Mb", padStorage));
        message("   disk free:" + Strings.padLeft("~" + session.diskFree() + " Mb", padStorage));
        message("");
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
