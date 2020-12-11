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
package net.oneandone.stool.server.values;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.Type;
import net.oneandone.stool.server.settings.Expire;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A short-lived object, created for one request, discarded afterwards - caches results for performance.
 */
public final class Helm {
    /**
     * @return imageOrRepository exact image or repository to publish latest image from
     */
    public static String run(Server server, String name, boolean upgrade, Map<String, Object> map, String imageOrRepository, Map<String, String> clientValues)
            throws IOException {
        Expressions expressions;
        Application app;
        World world;
        FileNode tmp;
        TagInfo image;
        FileNode values;
        FileNode src;
        Expire expire;
        Registry registry;

        validateRepository(Registry.toRepository(imageOrRepository));
        world = World.create(); // TODO
        registry = server.createRegistry(world, imageOrRepository);
        image = registry.resolve(imageOrRepository);
        expressions = new Expressions(world, server, image, server.stageFqdn(name));
        app = Application.load(expressions, world.file("/etc/charts/app.yaml").readString());
        tmp = world.getTemp().createTempDirectory();
        values = world.getTemp().createTempFile();
        src = world.file("/etc/charts").join(app.chart);
        if (!src.isDirectory()) {
            throw new ArgumentException("helm chart not found: " + app.chart);
        }
        src.copyDirectory(tmp);
        Type.TYPE.checkValues(clientValues, builtInValues(tmp).keySet());
        app.addValues(expressions, map);
        map.putAll(clientValues);
        expire = Expire.fromHuman((String) map.getOrDefault(Type.VALUE_EXPIRE, Integer.toString(server.settings.defaultExpire)));
        if (expire.isExpired()) {
            throw new ArgumentException(name + ": stage expired: " + expire);
        }
        map.put(Type.VALUE_EXPIRE, expire.toString()); // normalize
        Server.LOGGER.info("values: " + map);
        try (PrintWriter v = new PrintWriter(values.newWriter())) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                v.println(entry.getKey() + ": " + toJson(entry.getValue()));
            }
        }
        try {
            Server.LOGGER.info("helm install upgrade=" + upgrade);
            Server.LOGGER.info(tmp.exec("helm", upgrade ? "upgrade" : "install", "--debug", "--values", values.getAbsolute(), name, tmp.getAbsolute()));
        } finally {
            tmp.deleteTree();
        }
        return image.repositoryTag;
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
