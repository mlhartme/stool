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
package net.oneandone.stool.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import net.oneandone.stool.cli.Client;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.Reference;
import net.oneandone.sushi.util.Strings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Ls extends InfoCommand {
    /** to delay output until I can determine column widths*/
    private final LinkedHashMap<String, List<String>> columns;

    public Ls(Globals globals, String stage) {
        super(globals, stage);
        columns = new LinkedHashMap<>();
    }

    @Override
    public CompoundResult runAll() throws Exception {
        CompoundResult result;

        if (stageClause == null) { // list command has an implicit -all switch
            stageClause = "";
        }
        doBefore();
        result = super.runAll();
        doAfter();
        return result;
    }

    private void doBefore() {
        if (selected.isEmpty()) {
            selected.addAll(Arrays.asList("name", "image", "last-deployed"));
        }
        for (String infoName : selected) {
            columns.put(infoName, new ArrayList<>());
        }
    }

    private void doAfter() {
        final String gap = "   ";
        List<Integer> widths;
        int idx;

        widths = widths();
        idx = 0;
        for (String name : columns.keySet()) {
            console.info.print(gap);
            console.info.print(Strings.padRight("(" + name + ")", widths.get(idx)));
            idx++;
        }
        console.info.println();
        for (int i = 0, lines = lines(); i < lines; i++) {
            idx = 0;
            for (List<String> column : columns.values()) {
                console.info.print(gap);
                console.info.print(Strings.padRight(column.get(i), widths.get(idx)));
                idx++;
            }
            console.info.println();
        }
        message("");
    }


    @Override
    public void doRun(Client client, String clientFilter, CompoundResult result) throws Exception {
        Map<String, Map<String, JsonNode>> response;

        response = client.list(clientFilter, selected);
        for (Map.Entry<String, Map<String, JsonNode>> stage : response.entrySet()) {
            for (Map.Entry<String, JsonNode> entry : stage.getValue().entrySet()) {
                columns.get(entry.getKey()).add(infoToString(entry.getValue()));
            }
            result.success(new Reference(client, stage.getKey()));
        }
    }

    private int lines() {
        int result;

        result = -1;
        for (List<String> column : columns.values()) {
            if (result == -1) {
                return column.size();
            } else {
                if (column.size() != result) {
                    throw new IllegalStateException(column + " vs " + result);
                }
            }
        }
        return result;
    }

    private List<Integer> widths() {
        List<Integer> result;
        int max;

        result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : columns.entrySet()) {
            max = entry.getKey().length() + 2;
            for (String cell : entry.getValue()) {
                max = Math.max(max, cell.length());
            }
            result.add(max);
        }
        return result;
    }
}
