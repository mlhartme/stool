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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Configuration;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.core.Type;
import net.oneandone.stool.util.Expire;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * A short-lived object, created for one request, discarded afterwards - caches results for performance.
 */
public final class Helm {
    private static final Logger LOGGER = LoggerFactory.getLogger(Helm.class);
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

    private Helm() {
    }
}
