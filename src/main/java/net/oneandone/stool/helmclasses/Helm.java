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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Configuration;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
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
        Map<String, Clazz> all;
        Expressions expressions;
        String applicationName;
        Clazz application;
        FileNode chart;
        FileNode values;

        world = root.getWorld();
        expressions = new Expressions(world, configuration, image, configuration.stageFqdn(name));
        all = Clazz.loadAll(root);
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

    private Helm() {
    }
}
