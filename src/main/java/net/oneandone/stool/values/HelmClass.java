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
import net.oneandone.stool.util.Json;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/** represents the applications file */
public class HelmClass {
    private static final Logger LOGGER = LoggerFactory.getLogger(HelmClass.class);

    public static String install(FileNode root, Configuration configuration, String name, String imageOrRepository,
                                 String applicationOpt, Map<String, String> clientValues) throws IOException {
        return helm(root, configuration, name, false, new HashMap<>(), imageOrRepository, applicationOpt, clientValues);
    }

    public static String upgrade(FileNode root, Configuration configuration, String name, Map<String, Object> map,
                                 String imageOrRepository, String applicationOpt, Map<String, String> clientValues) throws IOException {
        return helm(root, configuration, name, true, map, imageOrRepository, applicationOpt, clientValues);
    }
    /**
     * @return imageOrRepository exact image or repository to publish latest tag from
     */
    private static String helm(FileNode root, Configuration configuration, String name, boolean upgrade, Map<String, Object> map,
                              String imageOrRepository, String applicationOpt, Map<String, String> clientValues)
            throws IOException {
        TagInfo image;
        Registry registry;

        validateRepository(Registry.toRepository(imageOrRepository));
        registry = configuration.createRegistry(root.getWorld(), imageOrRepository);
        image = registry.resolve(imageOrRepository);
        return helm(root, configuration, name, upgrade, map, image, applicationOpt, clientValues);
    }

    /**
     * @return image actually published
     */
    private static String helm(FileNode root, Configuration configuration, String name,
                              boolean upgrade, Map<String, Object> map, TagInfo image, String applicationOpt,
                              Map<String, String> clientValues)
            throws IOException {
        World world;
        Map<String, HelmClass> all;
        Expressions expressions;
        String applicationName;
        HelmClass application;
        FileNode chart;
        FileNode values;

        world = root.getWorld();
        expressions = new Expressions(world, configuration, image, configuration.stageFqdn(name));
        all = HelmClass.loadAll(root);
        applicationName = applicationOpt != null ? applicationOpt : image.labels.getOrDefault("helm.application", "default");
        LOGGER.info("application: " + applicationName);
        application = all.get(applicationName);
        if (application == null) {
            throw new IOException("unknown application: " + applicationName);
        }
        chart = root.join(application.chart).checkDirectory();
        LOGGER.info("chart: " + application.chart);
        values = application.createValuesFile(expressions, clientValues, map);
        try {
            LOGGER.info("values: " + values.readString());
            LOGGER.info("helm install upgrade=" + upgrade);
            LOGGER.info(chart.exec("helm", upgrade ? "upgrade" : "install", "--debug", "--values", values.getAbsolute(), name, chart.getAbsolute()));
        } finally {
            values.deleteFile();
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

    public static Map<String, HelmClass> loadAll(FileNode root) throws IOException {
        ObjectMapper yaml;
        Iterator<JsonNode> classes;
        Iterator<Map.Entry<String, JsonNode>> values;
        Map.Entry<String, JsonNode> entry;
        Map<String, HelmClass> result;
        ObjectNode clazz;
        String extendz;
        String chart;
        HelmClass base;
        HelmClass app;
        String name;


        yaml = new ObjectMapper(new YAMLFactory());
        try (Reader src = root.join("classes.yaml").newReader()) {
            classes = yaml.readTree(src).elements();
        }
        result = new HashMap<>();
        while (classes.hasNext()) {
            clazz = (ObjectNode) classes.next();
            name = clazz.get("name").asText();
            chart = Json.stringOpt(clazz, "chart");
            extendz = Json.stringOpt(clazz, "extends");
            if (chart == null && extendz == null) {
                throw new IOException("chart or extends expected");
            }
            if (chart != null && extendz != null) {
                throw new IOException("chart and extends cannot be combined");
            }
            if (chart != null) {
                app = new HelmClass(name, chart, new HashMap<>());
            } else {
                base = result.get(extendz);
                if (base == null) {
                    throw new IOException("class not found: " + extendz);
                }
                app = base.newInstance(name);
            }
            result.put(app.name, app);
            values = clazz.get("values").fields();
            while (values.hasNext()) {
                entry = values.next();
                name = entry.getKey();
                app.values.put(name, Value.forYaml(name, entry.getValue()));
            }
        }
        return result;
    }

    private static Collection<String> loadValueKeys(ObjectMapper yaml, FileNode valuesYaml) throws IOException {
        ObjectNode values;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;
        Collection<String> result;

        try (Reader src = valuesYaml.newReader()) {
            values = (ObjectNode) yaml.readTree(src);
        }
        result = new HashSet();
        iter = values.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.add(entry.getKey());
        }
        return result;
    }

    //--

    public final String name;
    public final String chart;
    public final Map<String, Value> values;

    private HelmClass(String name, String chart, Map<String, Value> values) {
        this.name = name;
        this.chart = chart;
        this.values = values;
    }

    public HelmClass newInstance(String withName) {
        return new HelmClass(withName, chart, new HashMap<>(values));
    }

    public FileNode createValuesFile(Expressions builder, Map<String, String> clientValues, Map<String, Object> dest) throws IOException {
        String key;
        Expire expire;
        FileNode file;

        for (Value field : this.values.values()) {
            if (field != null) {
                dest.put(field.name, builder.eval(field.macro));
            }
        }
        for (Map.Entry<String, String> entry : clientValues.entrySet()) {
            key = entry.getKey();
            if (!this.values.containsKey(key)) {
                throw new ArgumentException("unknown value: " + key);
            }
            dest.put(key, entry.getValue());
        }

        // normalize expire
        expire = Expire.fromHuman((String) dest.getOrDefault(Type.VALUE_EXPIRE, Integer.toString(builder.configuration.defaultExpire)));
        if (expire.isExpired()) {
            throw new ArgumentException("stage expired: " + expire);
        }
        dest.put(Type.VALUE_EXPIRE, expire.toString()); // normalize

        file = builder.world.getTemp().createTempFile();
        try (PrintWriter v = new PrintWriter(file.newWriter())) {
            for (Map.Entry<String, Object> entry : dest.entrySet()) {
                v.println(entry.getKey() + ": " + toJson(entry.getValue()));
            }
        }
        return file;
    }
}
