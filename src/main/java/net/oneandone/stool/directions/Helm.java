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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Dependencies;
import net.oneandone.stool.core.LocalSettings;
import net.oneandone.stool.util.Diff;
import net.oneandone.stool.util.Expire;
import net.oneandone.stool.util.Json;
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

    public static void install(String kubernetesContext, LocalSettings localSettings, String name, DirectionsRef directionsRef, Map<String, String> overrides)
            throws IOException {
        Directions directions;

        directions = directionsRef.resolve(localSettings);
        helm(kubernetesContext, localSettings, name, false, false, null, directions, overrides, Collections.emptyMap());
    }

    public static Diff upgrade(String kubeContext, LocalSettings localSettings, String name, boolean dryrun, List<String> allow,
                               Directions directions, Map<String, String> overrides, Map<String, String> prev) throws IOException {
        return helm(kubeContext, localSettings, name, true, dryrun, allow, directions, overrides, prev);
    }

    private static Diff helm(String kubeContext, LocalSettings localSettings, String name, boolean upgrade, boolean dryrun, List<String> allowOpt,
                             Directions origDirections, Map<String, String> overrides, Map<String, String> prev)
            throws IOException {
        Toolkit toolkit;
        Directions directions;
        Freemarker freemarker;
        Chart chart;
        FileNode valuesFile;
        Directions tmpDirections;
        Map<String, String> values;
        Diff result;
        Diff forbidden;

        toolkit = localSettings.toolkit();
        directions = origDirections.merged(toolkit);
        if (directions.chartOpt == null) {
            throw new IOException("directions without chart: " + directions.subject);
        }
        LOGGER.info("chart: " + directions.chartOpt + ":" + directions.chartVersionOpt);
        freemarker = toolkit.freemarker(localSettings.lib, name, localSettings.fqdn);
        tmpDirections = directions.merged(toolkit);
        tmpDirections.setValues(overrides);
        toolkit = localSettings.toolkit();
        chart = toolkit.chart(tmpDirections.chartOpt);
        values = freemarker.eval(prev, tmpDirections, toolkit.scripts);
        result = Diff.diff(prev, values);
        if (allowOpt != null) {
            forbidden = result.withoutKeys(allowOpt);
            if (!forbidden.isEmpty()) {
                throw new IOException("change is forbidden:\n" + forbidden);
            }
        }
        // wipe private keys
        for (Direction property : tmpDirections.directions.values()) {
            if (property.privt) {
                result.remove(property.name);
            }
        }
        valuesFile = createValuesFile(localSettings.yaml, localSettings.world, values, directions);
        try {
            LOGGER.info("values: " + valuesFile.readString());
            exec(dryrun, kubeContext,
                    toolkit.scripts, upgrade ? "upgrade" : "install", "--debug", "--values", valuesFile.getAbsolute(), name, chart.reference);
            return result;
        } finally {
            valuesFile.deleteFile();
        }
    }

    private static FileNode createValuesFile(ObjectMapper yaml, World world, Map<String, String> actuals, Directions directions) throws IOException {
        ObjectNode dest;
        Expire expire;
        FileNode file;
        String str;

        dest = yaml.createObjectNode();
        for (Map.Entry<String, String> entry : actuals.entrySet()) {
            dest.put(entry.getKey(), entry.getValue());
        }

        dest.set(Directions.DIRECTIONS_VALUE, directions.toObject(yaml));

        // check expire - TODO: ugly up reference to core package
        str = Json.string(dest, Dependencies.VALUE_EXPIRE, null);
        if (str != null) {
            expire = Expire.fromString(str);
            if (expire.isExpired()) {
                throw new ArgumentException("stage expired: " + expire);
            }
            dest.put(Dependencies.VALUE_EXPIRE, expire.toString());
        }

        file = world.getTemp().createTempFile().writeString(dest.toPrettyString());
        return file;
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
