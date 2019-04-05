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

import net.oneandone.inline.Console;
import net.oneandone.stool.common.Reference;
import net.oneandone.stool.client.Server;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Ls extends InfoCommand {
    private final List<List<String>> lines;

    public Ls(World world, Console console, Server server, String defaults) {
        super(world, console, server, defaults);
        lines = new ArrayList<>();
    }

    @Override
    public boolean doBefore(List<Reference> referencesNotUsed, int indent) {
        List<String> line;

        if (selected.isEmpty()) {
            selected.addAll(defaults());
            if (selected.isEmpty()) {
                selected.addAll(Arrays.asList("name", "state", "last-modified-by" /* TODO , "origin", "directory" */));
            }
        }
        header("stages");

        line = new ArrayList<>();
        lines.add(line);
        for (String infoName : selected) {
            line.add('(' + infoName + ')');
        }
        return true;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        List<String> line;

        line = new ArrayList<>();
        lines.add(line);
        for (Map.Entry<String, String> entry : server.status(reference, selected).entrySet()) {
            line.add(entry.getValue().replace("\t", " "));
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
        message("        mem: " + Strings.padLeft("~" + server.memUnreserved() + " mb free", padStorage));
        quota();
        message("");
    }

    private void quota() throws IOException {
        String quota;

        quota = server.quota();
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

    protected List<Reference> defaultSelected(Map<String, IOException> problems) throws IOException {
        return server.search(null, problems);
    }
}
