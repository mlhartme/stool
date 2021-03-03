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
package net.oneandone.stool.classes;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Settings;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.util.Diff;
import net.oneandone.stool.util.Versions;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Helm {
    private static final Logger LOGGER = LoggerFactory.getLogger(Helm.class);

    public static void install(String kubernetesContext, Settings settings, String name, ClassRef classRef, Map<String, String> values)
            throws IOException {
        Clazz clazz;

        clazz = classRef.resolve(kubernetesContext, settings);
        helm(kubernetesContext, settings, name, false, false, null, clazz, values, Collections.emptyMap());
    }

    public static Diff upgrade(String kubeContext, Settings settings, String name, boolean dryrun, List<String> allow,
                               Clazz clazz, Map<String, String> values, Map<String, String> prev) throws IOException {
        return helm(kubeContext, settings, name, true, dryrun, allow, clazz, values, prev);
    }

    private static Diff helm(String kubeContext, Settings settings, String name, boolean upgrade, boolean dryrun, List<String> allowOpt,
                             Clazz originalClass, Map<String, String> clientValues, Map<String, String> prev)
            throws IOException {
        World world;
        Map<String, FileNode> charts;
        Expressions expressions;
        FileNode chart;
        FileNode values;
        Clazz modifiedClass;
        Map<String, String> next;
        Diff result;
        Diff forbidden;

        charts = settings.local.resolvedCharts(kubeContext);
        world = settings.world;
        expressions = new Expressions(world, settings, name);
        modifiedClass = originalClass.derive(originalClass.origin, originalClass.author, originalClass.name);
        modifiedClass.setValues(clientValues);
        chart = charts.get(modifiedClass.chart).checkDirectory();
        LOGGER.info("chart: " + modifiedClass.chart + ":" + modifiedClass.chartVersion);
        next = expressions.eval(prev, modifiedClass, chart);
        result = Diff.diff(prev, next);
        if (allowOpt != null) {
            forbidden = result.withoutKeys(allowOpt);
            if (!forbidden.isEmpty()) {
                throw new IOException("change is forbidden:\n" + forbidden);
            }
        }
        // wipe private keys
        for (Property property : modifiedClass.properties.values()) {
            if (property.privt) {
                result.remove(property.name);
            }
        }
        values = modifiedClass.createValuesFile(settings, next);
        try {
            LOGGER.info("values: " + values.readString());
            exec(dryrun, kubeContext,
                    chart, upgrade ? "upgrade" : "install", "--debug", "--values", values.getAbsolute(), name, chart.getAbsolute());
            return result;
        } finally {
            values.deleteFile();
        }
    }

    //--

    public static FileNode tagFile(FileNode chart) {
        return chart.join(".tag");
    }

    public static FileNode resolveRepositoryChart(String kubeContext, PortusRegistry registry, String repository, FileNode exports) throws IOException {
        String chart;
        List<String> tags;
        String tag;
        String existing;
        FileNode chartDir;
        FileNode tagFile;

        chart = chart(repository);
        tags = sortTags(registry.helmTags(Registry.getRepositoryPath(repository)));
        if (tags.isEmpty()) {
            throw new IOException("no tag for repository " + repository);
        }
        tag = tags.get(tags.size() - 1);
        chartDir = exports.join(chart);
        tagFile = tagFile(chartDir);
        if (chartDir.exists()) {
            existing = tagFile.readString().trim();
            if (!tag.equals(existing)) {
                LOGGER.info("updating chart " + chart + " " + existing + " -> " + tag);
                chartDir.deleteTree();
            }
        } else {
            LOGGER.info("loading chart " + chart + " " + tag);
        }
        if (!chartDir.exists()) {
            Helm.exec(false, kubeContext, exports, "chart", "pull", repository + ":" + tag);
            Helm.exec(false, kubeContext, exports, "chart", "export", repository + ":" + tag, "-d", chartDir.getParent().getAbsolute());
            if (!chartDir.exists()) {
                throw new IllegalStateException(chartDir.getAbsolute());
            }
            tagFile.writeString(tag);
        }
        return chartDir;
    }

    private static String chart(String repository) {
        int idx;

        idx = repository.lastIndexOf('/');
        if (repository.contains(":") || idx < 0 || idx + 1 == repository.length()) {
            throw new ArgumentException("invalid chart repository: " + repository);
        }
        return repository.substring(idx + 1);
    }

    private static List<String> sortTags(List<String> lst) { // TODO: also use for taginfo sorting, that's still based on numbers
        Collections.sort(lst, Versions.CMP);
        return lst;
    }

    //--

    public static void exec(boolean dryrun, String kubeContext, FileNode dir, String... args) throws IOException {
        String[] cmd;

        if (kubeContext != null) {
            cmd = Strings.cons("--kube-context", Strings.cons(kubeContext, args));
        } else {
            cmd = args;
        }
        cmd = Strings.cons("helm", cmd);
        LOGGER.debug(Arrays.asList(cmd).toString());
        if (dryrun) {
            LOGGER.info("dryrun - skipped");
        } else {
            LOGGER.info(dir.exec(cmd));
        }
    }

    private Helm() {
    }
}
