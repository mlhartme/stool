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
import net.oneandone.stool.client.Client;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.util.Map;

public class Status extends InfoCommand {
    public Status(Globals globals, World world, Console console, String defaults) {
        super(globals, world, console, defaults);
    }

    private static final Separator TAB = Separator.on('\t');

    @Override
    public void doMain(Client client, String stage) throws Exception {
        Map<String, String> infos;
        int width;
        boolean first;
        String value;

        infos = client.status(stage, selected.isEmpty() ? defaults() : selected);
        width = 0;
        for (String name : infos.keySet()) {
            width = Math.max(width, name.length());
        }
        width += 2;
        for (Map.Entry<String, String> entry : infos.entrySet()) {
            console.info.print(Strings.times(' ', width - entry.getKey().length()));
            console.info.print(entry.getKey());
            console.info.print(" : ");
            first = true;
            value = entry.getValue();
            if (value.isEmpty()) {
                console.info.println();
            } else for (String str : TAB.split(value)) {
                if (first) {
                    first = false;
                } else {
                    console.info.print(Strings.times(' ', width + 3));
                }
                console.info.println(str);
            }
        }
    }
}
