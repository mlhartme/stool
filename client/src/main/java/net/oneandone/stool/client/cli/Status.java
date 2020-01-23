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
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.util.Collection;
import java.util.Map;

public class Status extends InfoCommand {
    public Status(Globals globals) {
        super(globals);
    }

    private static final Separator TAB = Separator.on('\t');

    @Override
    public void doRun(Client client, String clientFilter) throws Exception {
        Map<String, Map<String, JsonElement>> response;
        boolean withPrefix;
        int prefixWidth;
        String prefix;
        String name;

        response = client.list(clientFilter, selected);
        withPrefix = response.size() != 1;
        prefixWidth = maxWidth(response.keySet());
        for (Map.Entry<String, Map<String, JsonElement>> stage : response.entrySet()) {
            if (withPrefix) {
                name = stage.getKey();
                prefix = Strings.times(' ', prefixWidth - name.length());
                prefix = prefix + "{" + name + "@" + client.getName() + "} ";
            } else {
                prefix = "";
            }
            output(prefix, stage.getValue());
        }
    }

    public void output(String prefix, Map<String, JsonElement> infos) {
        int width;

        width = maxWidth(infos.keySet()) + 2;
        for (Map.Entry<String, JsonElement> entry : infos.entrySet()) {
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
