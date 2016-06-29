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

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Status extends StatusBase {
    public Status(Session session) {
        super(session);
    }

    private static Separator TAB = Separator.on('\t');

    @Override
    public void doRun(Stage stage) throws Exception {
        List<Field> fields;
        Map<Field, Object> status;
        int width;
        boolean first;
        String value;

        status = status(session, processes(), stage);
        fields = selected.isEmpty() ? Arrays.asList(Field.values()) : selected;
        width = 0;
        for (Field field : fields) {
            width = Math.max(width, field.length());
        }
        width += 2;
        for (Field field : fields) {
            console.info.print(Strings.times(' ', width - field.length()));
            console.info.print(field.toString());
            console.info.print(" : ");
            first = true;
            value = toString(status.get(field));
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
