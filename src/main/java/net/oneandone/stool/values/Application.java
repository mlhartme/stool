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
package net.oneandone.stool.values;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Application {
    public static Application load(FileNode valuesFile) throws IOException {
        ObjectMapper yaml;
        ObjectNode root;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;
        Application result;
        ObjectNode clazz;

        yaml = new ObjectMapper(new YAMLFactory());
        try (Reader src = valuesFile.newReader()) {
            root = (ObjectNode) yaml.readTree(src);
        }
        result = new Application();
        clazz = null;
        iter = root.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            if ("class".equals(entry.getKey())) {
                clazz = (ObjectNode) entry.getValue();
            } else {
                result.values.put(entry.getKey(), entry.getValue().asText());
            }
        }
        if (clazz == null) {
            throw new IllegalStateException("missing class field: " + valuesFile);
        }
        iter = clazz.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.fields.add(new Field(entry.getKey(), entry.getValue().asText()));
        }
        return result;
    }

    // class fields
    private final List<Field> fields;
    public final Map<String, String> values;

    public Application() {
        this.fields = new ArrayList<>();
        this.values = new HashMap<>();
    }

    public void addValues(Expressions builder, Map<String, Object> map) throws IOException {
        for (Field field : fields) {
            map.put(field.name, builder.eval(field.macro));
        }
    }
}
