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
package net.oneandone.stool.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Application {
    private final List<Field> fields;

    private static class Field {
        public final String name;
        public final String macro;

        private Field(String name, String macro) {
            this.name = name;
            this.macro = macro;
        }
    }

    public Application() {
        this.fields = new ArrayList<>();

        fields.add(new Field("image", "image"));
        fields.add(new Field("fqdn", "fqdn"));
        fields.add(new Field("cert", "cert"));
        fields.add(new Field("fault", "fault"));
    }

    public void addValues(Macros builder, Map<String, Object> map) throws IOException {
        for (Field field : fields) {
            map.put(field.name, builder.eval(field.macro));
        }
    }
}
