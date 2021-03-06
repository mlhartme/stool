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

import java.util.Collection;
import java.util.Map;

public class Status extends InfoCommand {
    private final boolean hidden;

    public Status(Globals globals, boolean hidden, String stage) {
        super(globals, stage);
        this.hidden = hidden;
    }

    @Override
    public void doRun(Client client, String clientFilter, CompoundResult result) throws Exception {
        Map<String, Map<String, JsonNode>> response;
        boolean withPrefix;
        int prefixWidth;
        String prefix;
        String name;

        response = client.list(clientFilter, selected, hidden);
        withPrefix = response.size() != 1;
        prefixWidth = maxWidth(response.keySet());
        for (Map.Entry<String, Map<String, JsonNode>> stage : response.entrySet()) {
            name = stage.getKey();
            if (withPrefix) {
                prefix = Strings.times(' ', prefixWidth - name.length());
                prefix = prefix + "{" + name + "} ";
            } else {
                prefix = "";
            }
            output(prefix, stage.getValue());
            result.success(new Reference(client, name));
        }
    }

    public void output(String prefix, Map<String, JsonNode> infos) {
        int width;

        width = maxWidth(infos.keySet()) + 2;
        for (Map.Entry<String, JsonNode> entry : infos.entrySet()) {
            console.info.print(prefix);
            console.info.print(Strings.times(' ', width - entry.getKey().length()));
            console.info.print(entry.getKey());
            console.info.print(" : ");
            console.info.println(infoToString(entry.getValue()));
        }
    }

    private static int maxWidth(Collection<String> names) {
        int width;

        width = 0;
        for (String name : names) {
            width = Math.max(width, name.length());
        }
        return width;
    }
}
