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
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.core.Server;
import net.oneandone.stool.core.Type;
import net.oneandone.stool.server.settings.Expire;
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
import java.util.Set;

/**
 * A short-lived object, created for one request, discarded afterwards - caches results for performance.
 */
public final class Helm {
    private static final Logger LOGGER = LoggerFactory.getLogger(Helm.class);
    /**
     * @return imageOrRepository exact image or repository to publish latest tag from
     */
    public static String run(FileNode root, Server server, String name,
                             boolean upgrade, Map<String, Object> map, String imageOrRepository, Map<String, String> clientValues)
            throws IOException {
        TagInfo image;
        Registry registry;

        validateRepository(Registry.toRepository(imageOrRepository));
        registry = server.configuration.createRegistry(root.getWorld(), imageOrRepository);
        image = registry.resolve(imageOrRepository);
        return run(root, server, name, upgrade, map, image, clientValues);
    }

    /**
     * @return imageOrRepository exact image or repository to publish latest image from
     */
    public static String run(FileNode root, Server server, String name, boolean upgrade, Map<String, Object> map, TagInfo image, Map<String, String> clientValues)
            throws IOException {
        Expressions expressions;
        Application app;
        FileNode tmp;
        FileNode values;
        FileNode src;
        Expire expire;

        expressions = new Expressions(root.getWorld(), server, image, server.configuration.stageFqdn(name));
        app = Application.load(expressions, root.join("app.yaml").readString());
        tmp = root.getWorld().getTemp().createTempDirectory();
        values = root.getWorld().getTemp().createTempFile();
        src = root.join(app.chart);
        if (!src.isDirectory()) {
            throw new ArgumentException("helm chart not found: " + app.chart);
        }
        src.copyDirectory(tmp);
        checkValues(clientValues, builtInValues(tmp).keySet());
        app.addValues(expressions, map);
        map.putAll(clientValues);
        expire = Expire.fromHuman((String) map.getOrDefault(Type.VALUE_EXPIRE, Integer.toString(server.configuration.defaultExpire)));
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
            LOGGER.info(tmp.exec("helm", upgrade ? "upgrade" : "install", "--debug", "--values", values.getAbsolute(), name, tmp.getAbsolute()));
        } finally {
            tmp.deleteTree();
        }
        return image.repositoryTag;
    }

    public static void checkValues(Map<String, String> clientValues, Collection<String> builtIns) {
        Set<String> unknown;

        unknown = new HashSet<>(clientValues.keySet());
        unknown.removeAll(builtIns);
        if (!unknown.isEmpty()) {
            throw new ArgumentException("unknown value(s): " + unknown);
        }
    }

    public static Map<String, String> builtInValues(FileNode chart) throws IOException {
        ObjectMapper yaml;
        ObjectNode root;
        Map<String, String> result;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        yaml = new ObjectMapper(new YAMLFactory());
        try (Reader src = chart.join("values.yaml").newReader()) {
            root = (ObjectNode) yaml.readTree(src);
        }
        result = new HashMap<>();
        iter = root.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.put(entry.getKey(), entry.getValue().asText());
        }
        return result;
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

    private Helm() {
    }
}
