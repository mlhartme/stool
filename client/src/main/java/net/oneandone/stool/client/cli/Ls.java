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
package net.oneandone.stool.client.cli;

import com.google.gson.JsonElement;
import net.oneandone.stool.client.Client;
import net.oneandone.stool.client.Globals;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Ls extends InfoCommand {
    /** to delay output until I can determine column widths*/
    private final List<List<String>> lines;

    public Ls(Globals globals) {
        super(globals);
        lines = new ArrayList<>();
    }

    @Override
    public EnumerationFailed runAll() throws Exception {
        EnumerationFailed result;

        if (stageClause == null) { // list command has an implicit -all switch
            all = true;
        }
        doBefore();
        result = super.runAll();
        doAfter();
        return result;
    }

    private void doBefore() {
        List<String> line;

        if (selected.isEmpty()) {
            selected.addAll(Arrays.asList("name", "running", "last-modified-by" /* TODO , "origin", "directory" */));
        }
        header("stages");

        line = new ArrayList<>();
        lines.add(line);
        for (String infoName : selected) {
            line.add('(' + infoName + ')');
        }
    }

    private void doAfter() {
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
    }


    @Override
    public void doRun(Client client, String clientFilter) throws Exception {
        List<String> line;
        Map<String, Map<String, JsonElement>> response;

        response = client.list(clientFilter, selected);
        for (Map.Entry<String, Map<String, JsonElement>> stage : response.entrySet()) {
            line = new ArrayList<>();
            lines.add(line);
            for (Map.Entry<String, JsonElement> entry : stage.getValue().entrySet()) {
                line.add(infoToString(entry.getValue()));
            }
        }
    }

    // TODO
    private void quota(Client client) throws IOException {
        String quota;

        quota = client.quota();
        if (quota == null) {
            message(" disk: quota disabled");
        } else {
            message(" disk: " + quota + " mb reserved");
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
}
