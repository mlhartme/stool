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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Application {
    public static Application load(Expressions expressions, String str) throws IOException {
        ObjectMapper mapper;
        ObjectNode root;
        ObjectNode values;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;
        Application result;

        mapper = new ObjectMapper(new YAMLFactory());
        root = (ObjectNode) mapper.readTree(new StringReader(str));
        result = new Application(expressions.eval(root.get("chart").asText()));
        values = (ObjectNode) root.get("values");
        iter = values.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.fields.add(new Field(entry.getKey(), entry.getValue().asText()));
        }
        return result;
    }

    public final String chart;
    private final List<Field> fields;

    public Application(String chart) {
        this.chart = chart;
        this.fields = new ArrayList<>();
    }

    public void addValues(Expressions builder, Map<String, Object> map) throws IOException {
        for (Field field : fields) {
            map.put(field.name, builder.eval(field.macro));
        }
    }
}
