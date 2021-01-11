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
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Configuration;
import net.oneandone.stool.core.Type;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.util.Expire;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HelmClass {
    private static final Logger LOGGER = LoggerFactory.getLogger(HelmClass.class);
    /**
     * @return imageOrRepository exact image or repository to publish latest tag from
     */
    public static String run(FileNode root, Configuration configuration, String name,
                             boolean upgrade, Map<String, Object> map, String imageOrRepository, Map<String, String> clientValues)
            throws IOException {
        TagInfo image;
        Registry registry;

        validateRepository(Registry.toRepository(imageOrRepository));
        registry = configuration.createRegistry(root.getWorld(), imageOrRepository);
        image = registry.resolve(imageOrRepository);
        return run(root.join("kutter") /* TODO */, configuration, name, upgrade, map, image, clientValues);
    }

    /**
     * @return imageOrRepository exact image or repository to publish latest image from
     */
    public static String run(FileNode src, Configuration configuration, String name,
                             boolean upgrade, Map<String, Object> map, TagInfo image, Map<String, String> clientValues)
            throws IOException {
        World world;
        Expressions expressions;
        HelmClass clazz;
        FileNode chart;
        FileNode values;
        Expire expire;

        world = src.getWorld();
        expressions = new Expressions(world, configuration, image, configuration.stageFqdn(name));
        chart = world.getTemp().createTempDirectory();
        values = world.getTemp().createTempFile();
        if (!src.isDirectory()) {
            throw new ArgumentException("helm class not found: " + src.getAbsolute());
        }
        src.copyDirectory(chart);
        clazz = HelmClass.load(chart);
        clazz.checkValues(clientValues);
        clazz.addValues(expressions, map);
        map.putAll(clientValues);
        expire = Expire.fromHuman((String) map.getOrDefault(Type.VALUE_EXPIRE, Integer.toString(configuration.defaultExpire)));
        if (expire.isExpired()) {
            throw new ArgumentException(name + ": stage expired: " + expire);
        }
        map.put(Type.VALUE_EXPIRE, expire.toString()); // normalize
        LOGGER.info("values: " + map);
        try (PrintWriter v = new PrintWriter(values.newWriter())) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                v.println(entry.getKey() + ": " + toJson(entry.getValue()));
            }
        }
        try {
            LOGGER.info("helm install upgrade=" + upgrade);
            LOGGER.info(chart.exec("helm", upgrade ? "upgrade" : "install", "--debug", "--values", values.getAbsolute(), name, chart.getAbsolute()));
        } finally {
            chart.deleteTree();
        }
        return image.repositoryTag;
    }

    // this is to avoid engine 500 error reporting "invalid reference format: repository name must be lowercase"
    public static void validateRepository(String repository) {
        URI uri;

        if (repository.endsWith("/")) {
            throw new ArgumentException("invalid repository: " + repository);
        }
        try {
            uri = new URI(repository);
        } catch (URISyntaxException e) {
            throw new ArgumentException("invalid repository: " + repository);
        }
        if (uri.getHost() != null) {
            checkLowercase(uri.getHost());
        }
        checkLowercase(uri.getPath());
    }

    private static void checkLowercase(String str) {
        for (int i = 0, length = str.length(); i < length; i++) {
            if (Character.isUpperCase(str.charAt(i))) {
                throw new ArgumentException("invalid registry prefix: " + str);
            }
        }
    }

    private static String toJson(Object obj) {
        if (obj instanceof String) {
            return "\"" + obj + '"';
        } else {
            return obj.toString(); // ok für boolean and integer
        }
    }

    //--

    public static HelmClass load(FileNode directory) throws IOException {
        ObjectMapper yaml;
        ObjectNode root;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;
        HelmClass result;
        ObjectNode clazz;

        yaml = new ObjectMapper(new YAMLFactory());
        try (Reader src = directory.join("values.yaml").newReader()) {
            root = (ObjectNode) yaml.readTree(src);
        }
        result = new HelmClass();
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
            throw new IllegalStateException("missing class field: " + directory);
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
    private final Map<String, String> values;

    public HelmClass() {
        this.fields = new ArrayList<>();
        this.values = new HashMap<>();
    }

    public void addValues(Expressions builder, Map<String, Object> map) throws IOException {
        for (Field field : fields) {
            map.put(field.name, builder.eval(field.macro));
        }
    }

    public void checkValues(Map<String, String> clientValues) {
        Set<String> unknown;

        unknown = new HashSet<>(clientValues.keySet());
        unknown.removeAll(values.keySet());
        if (!unknown.isEmpty()) {
            throw new ArgumentException("unknown value(s): " + unknown);
        }
    }

}
