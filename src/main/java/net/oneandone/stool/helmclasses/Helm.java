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

import net.oneandone.stool.core.Configuration;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/** represents the applications file */
public final class Helm {
    private static final Logger LOGGER = LoggerFactory.getLogger(Helm.class);

    public static void install(Configuration configuration, String name, ClassRef classRef, Map<String, String> values)
            throws IOException {
        Clazz clazz;

        clazz = classRef.resolve(configuration);
        helm(configuration, name, false, clazz, values);
    }

    public static void upgrade(Configuration configuration, String name, Clazz clazz, Map<String, String> values) throws IOException {
        helm(configuration, name, true, clazz, values);
    }

    private static void helm(Configuration configuration, String name, boolean upgrade, Clazz originalClass, Map<String, String> clientValues)
            throws IOException {
        World world;
        FileNode root;
        Expressions expressions;
        FileNode chart;
        FileNode values;
        Clazz modifiedClass;

        root = null; // TODO configuration.charts;
        world = root.getWorld();
        expressions = new Expressions(world, configuration, configuration.stageFqdn(name));
        modifiedClass = originalClass.derive(originalClass.origin, originalClass.author, originalClass.name);
        modifiedClass.setValues(clientValues);
        modifiedClass.checkNotAbstract();
        chart = root.join(modifiedClass.chart).checkDirectory();
        LOGGER.info("chart: " + modifiedClass.chart);
        values = modifiedClass.createValuesFile(configuration.yaml, expressions);
        try {
            LOGGER.info("values: " + values.readString());
            exec(chart, upgrade ? "upgrade" : "install", "--debug", "--values", values.getAbsolute(), name, chart.getAbsolute());
        } finally {
            values.deleteFile();
        }
    }

    public static void exec(FileNode dir, String... args) throws IOException {
        String[] cmd;

        cmd = Strings.cons("helm", args);
        LOGGER.debug(Arrays.asList(cmd).toString());
        LOGGER.info(dir.exec(cmd));
    }

    private Helm() {
    }
}
