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
package net.oneandone.stool.directions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Dependencies;
import net.oneandone.stool.core.LocalSettings;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.util.Diff;
import net.oneandone.stool.util.Expire;
import net.oneandone.stool.util.Json;
import net.oneandone.stool.util.Versions;
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

    public static void install(String kubernetesContext, LocalSettings localSettings, String name, DirectionsRef classRef, Map<String, String> overrides)
            throws IOException {
        Directions directions;

        directions = classRef.resolve(kubernetesContext, localSettings);
        helm(kubernetesContext, localSettings, name, false, false, null, directions, overrides, Collections.emptyMap());
    }

    public static Diff upgrade(String kubeContext, LocalSettings localSettings, String name, boolean dryrun, List<String> allow,
                               Directions directions, Map<String, String> overrides, Map<String, String> prev) throws IOException {
        return helm(kubeContext, localSettings, name, true, dryrun, allow, directions, overrides, prev);
    }

    private static Diff helm(String kubeContext, LocalSettings localSettings, String name, boolean upgrade, boolean dryrun, List<String> allowOpt,
                             Directions directions, Map<String, String> overrides, Map<String, String> prev)
            throws IOException {
        Map<String, FileNode> charts;
        Expressions expressions;
        FileNode chart;
        FileNode valuesFile;
        Directions tmpClass;
        Map<String, String> values;
        Diff result;
        Diff forbidden;

        if (directions.chartOpt == null) {
            throw new IOException("class is a mixin: " + directions.name);
        }
        charts = localSettings.resolvedCharts(kubeContext);
        LOGGER.info("chart: " + directions.chartOpt + ":" + directions.chartVersionOpt);
        expressions = new Expressions(localSettings, name);
        tmpClass = Directions.extend(directions.origin, directions.author, directions.name, Collections.singletonList(directions));
        tmpClass.setValues(overrides);
        chart = charts.get(tmpClass.chartOpt).checkDirectory();
        values = expressions.eval(prev, tmpClass, chart);
        result = Diff.diff(prev, values);
        if (allowOpt != null) {
            forbidden = result.withoutKeys(allowOpt);
            if (!forbidden.isEmpty()) {
                throw new IOException("change is forbidden:\n" + forbidden);
            }
        }
        // wipe private keys
        for (Direction property : tmpClass.properties.values()) {
            if (property.privt) {
                result.remove(property.name);
            }
        }
        valuesFile = createValuesFile(localSettings, values, directions);
        try {
            LOGGER.info("values: " + valuesFile.readString());
            exec(dryrun, kubeContext,
                    chart, upgrade ? "upgrade" : "install", "--debug", "--values", valuesFile.getAbsolute(), name, chart.getAbsolute());
            return result;
        } finally {
            valuesFile.deleteFile();
        }
    }

    private static FileNode createValuesFile(LocalSettings localSettings, Map<String, String> actuals, Directions helmClass) throws IOException {
        ObjectNode dest;
        Expire expire;
        FileNode file;
        String str;

        dest = localSettings.yaml.createObjectNode();
        for (Map.Entry<String, String> entry : actuals.entrySet()) {
            dest.put(entry.getKey(), entry.getValue());
        }

        dest.set(Directions.DIRECTIONS_VALUE, helmClass.toObject(localSettings.yaml));

        // check expire
        str = Json.string(dest, Dependencies.VALUE_EXPIRE, null);
        if (str != null) {
            expire = Expire.fromString(str);
            if (expire.isExpired()) {
                throw new ArgumentException("stage expired: " + expire);
            }
            dest.put(Dependencies.VALUE_EXPIRE, expire.toString());
        }

        file = localSettings.world.getTemp().createTempFile().writeString(dest.toPrettyString());
        return file;
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
