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
package net.oneandone.stool.helmclasses;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Configuration;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/** represents the applications file */
public final class Helm {
    private static final Logger LOGGER = LoggerFactory.getLogger(Helm.class);

    public static void install(FileNode root, Configuration configuration, String name, ClassRef classRef, Map<String, String> values)
            throws IOException {
        ObjectMapper yaml;
        Clazz clazz;

        yaml = new ObjectMapper(new YAMLFactory());
        clazz = classRef.resolve(configuration, root.getWorld(), yaml, root);
        helm(yaml, root, configuration, name, false, false, new HashMap<>(), clazz, values);
    }

    public static void upgrade(FileNode root, Configuration configuration, String name, Map<String, Object> map, boolean publish,
                               Clazz clazz, Map<String, String> values) throws IOException {
        helm(new ObjectMapper(new YAMLFactory()), root, configuration, name, true, publish, map, clazz, values);
    }

    private static void helm(ObjectMapper yaml, FileNode root, Configuration configuration, String name, boolean upgrade, boolean publish, Map<String, Object> map,
                             Clazz clazz, Map<String, String> clientValues)
            throws IOException {
        World world;
        Expressions expressions;
        FileNode chart;
        FileNode values;

        world = root.getWorld();
        expressions = new Expressions(world, configuration, configuration.stageFqdn(name));
        if (publish) {
            for (ValueType vt : clazz.values.values()) {
                map.put(vt.name, vt.publish(expressions, (String) map.get(vt.name)));
            }
        }
        clazz.checkNotAbstract();
        chart = root.join(clazz.chart).checkDirectory();
        LOGGER.info("chart: " + clazz.chart);
        values = clazz.createValuesFile(yaml, expressions, clientValues, map);
        try {
            LOGGER.info("values: " + values.readString());
            LOGGER.info("helm install upgrade=" + upgrade);
            LOGGER.info(chart.exec("helm", upgrade ? "upgrade" : "install", "--debug", "--values", values.getAbsolute(), name, chart.getAbsolute()));
        } finally {
            values.deleteFile();
        }
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

    private Helm() {
    }
}
