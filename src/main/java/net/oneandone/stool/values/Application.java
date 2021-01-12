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
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** represents the applications file */
public class Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);
    /**
     * @return imageOrRepository exact image or repository to publish latest tag from
     */
    public static String helm(FileNode root, Configuration configuration, String name,
                              boolean upgrade, Map<String, Object> map, String imageOrRepository, Map<String, String> clientValues)
            throws IOException {
        TagInfo image;
        Registry registry;

        validateRepository(Registry.toRepository(imageOrRepository));
        registry = configuration.createRegistry(root.getWorld(), imageOrRepository);
        image = registry.resolve(imageOrRepository);
        return helm(root, configuration, name, upgrade, map, image, clientValues);
    }

    /**
     * @return image actually published
     */
    public static String helm(FileNode root, Configuration configuration, String name,
                              boolean upgrade, Map<String, Object> map, TagInfo image, Map<String, String> clientValues)
            throws IOException {
        World world;
        Map<String, Application> all;
        Expressions expressions;
        String applicationName;
        Application application;
        FileNode chart;
        FileNode values;
        Expire expire;

        world = root.getWorld();
        expressions = new Expressions(world, configuration, image, configuration.stageFqdn(name));
        all = Application.loadAll(root);
        applicationName = image.labels.getOrDefault("helm.application", "default");
        LOGGER.info("application: " + applicationName);
        application = all.get(applicationName);
        if (application == null) {
            throw new IOException("unknown application: " + applicationName);
        }
        LOGGER.info("chart: " + application.chart);
        application.checkValues(clientValues);
        application.addValues(expressions, map);
        chart = root.join(application.chart).checkDirectory();
        map.putAll(clientValues);
        expire = Expire.fromHuman((String) map.getOrDefault(Type.VALUE_EXPIRE, Integer.toString(configuration.defaultExpire)));
        if (expire.isExpired()) {
            throw new ArgumentException(name + ": stage expired: " + expire);
        }
        map.put(Type.VALUE_EXPIRE, expire.toString()); // normalize
        LOGGER.info("values: " + map);
        values = world.getTemp().createTempFile();
        try (PrintWriter v = new PrintWriter(values.newWriter())) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                v.println(entry.getKey() + ": " + toJson(entry.getValue()));
            }
        }
        LOGGER.info("helm install upgrade=" + upgrade);
        LOGGER.info(chart.exec("helm", upgrade ? "upgrade" : "install", "--debug", "--values", values.getAbsolute(), name, chart.getAbsolute()));
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

    public static Map<String, Application> loadAll(FileNode root) throws IOException {
        ObjectMapper yaml;
        ArrayNode all;
        Iterator<JsonNode> charts;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Iterator<Map.Entry<String, JsonNode>> derived;
        Map.Entry<String, JsonNode> entry;
        Map.Entry<String, JsonNode> derivedEntry;
        Map<String, Application> result;
        ObjectNode one;
        String c;
        Application base;
        Application app;
        String name;


        yaml = new ObjectMapper(new YAMLFactory());
        try (Reader src = root.join("applications.yaml").newReader()) {
            all = (ArrayNode) yaml.readTree(src);
        }
        result = new HashMap<>();
        charts = all.elements();
        while (charts.hasNext()) {
            one = (ObjectNode) charts.next();
            c = one.get("chart").asText();
            base = new Application(c);
            loadValues(yaml, root.join(c, "values.yaml"), base.values);
            iter = one.get("fields").fields();
            while (iter.hasNext()) {
                entry = iter.next();
                name = entry.getKey();
                base.fields.put(name, new Field(name, entry.getValue().asText()));
            }
            iter = one.get("applications").fields();
            while (iter.hasNext()) {
                entry = iter.next();
                app = base.newInstance();
                if (result.put(entry.getKey(), app) != null) {
                    throw new IOException("duplicate application: " + entry.getKey());
                }
                derived = entry.getValue().fields();
                while (derived.hasNext()) {
                    derivedEntry = derived.next();
                    name = derivedEntry.getKey();
                    app.fields.put(name, new Field(name, derivedEntry.getValue().asText()));
                }
            }
        }
        return result;
    }

    private static void loadValues(ObjectMapper yaml, FileNode valuesYaml, Map<String, String> dest) throws IOException {
        ObjectNode values;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        try (Reader src = valuesYaml.newReader()) {
            values = (ObjectNode) yaml.readTree(src);
        }
        iter = values.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            dest.put(entry.getKey(), entry.getValue().asText());
        }
    }

    //--

    public final String chart;
    private final Map<String, String> values;
    public final Map<String, Field> fields;

    public Application(String chart) {
        this(chart, new HashMap<>(), new HashMap<>());
    }

    private Application(String chart, Map<String, String> values, Map<String, Field> fields) {
        this.chart = chart;
        this.values = values;
        this.fields = fields;
    }

    public Application newInstance() {
        return new Application(chart, new HashMap<>(values), new HashMap<>(fields));
    }

    public void addValues(Expressions builder, Map<String, Object> map) throws IOException {
        for (Field field : fields.values()) {
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
