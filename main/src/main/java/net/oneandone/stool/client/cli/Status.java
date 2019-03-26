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

import net.oneandone.stool.common.Reference;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.Server;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.util.List;

public class Status extends InfoCommand {
    public Status(Server server, String defaults) {
        super(server, defaults);
    }

    private static final Separator TAB = Separator.on('\t');

    @Override
    public void doMain(Reference reference) throws Exception {
        List<Info> infos;
        int width;
        boolean first;
        String value;

        infos = server.status(reference, selected.isEmpty() ? defaults() : selected);
        width = 0;
        for (Info info : infos) {
            width = Math.max(width, info.name().length());
        }
        width += 2;
        for (Info info : infos) {
            console.info.print(Strings.times(' ', width - info.name().length()));
            console.info.print(info.name());
            console.info.print(" : ");
            first = true;
            value = info.getAsString();
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
