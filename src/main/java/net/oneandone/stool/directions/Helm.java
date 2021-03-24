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

import net.oneandone.stool.core.LocalSettings;
import net.oneandone.stool.core.Sequence;
import net.oneandone.stool.util.Diff;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class Helm {
    private static final Logger LOGGER = LoggerFactory.getLogger(Helm.class);

    public static Diff helm(String kubeContext, LocalSettings localSettings, String name, boolean upgrade, boolean dryrun, List<String> allowOpt,
                            Directions instance, Directions config, Map<String, String> prev) throws IOException {
        return helm(kubeContext, localSettings, name, upgrade, dryrun, allowOpt,
                new Sequence(instance.merged(localSettings.toolkit()), config), prev);
    }

    public static Diff helm(String kubeContext, LocalSettings localSettings, String name, boolean upgrade, boolean dryrun, List<String> allowOpt,
                            Sequence sequence, Map<String, String> prev)
            throws IOException {
        Toolkit toolkit;
        Freemarker freemarker;
        FileNode valuesFile;
        Map<String, String> values;
        Diff result;
        Diff forbidden;

        toolkit = localSettings.toolkit();
        if (sequence.merged.chartOpt == null) {
            throw new IOException("directions without chart: " + sequence.merged.subject);
        }
        LOGGER.info("chart: " + sequence.merged.chartOpt + ":" + sequence.merged.chartVersionOpt);
        freemarker = toolkit.freemarker(localSettings.getLib(), name, localSettings.fqdn);
        values = freemarker.eval(prev, sequence.configMerged(toolkit), toolkit.scripts);
        result = Diff.diff(prev, values);
        if (allowOpt != null) {
            forbidden = result.withoutKeys(allowOpt);
            if (!forbidden.isEmpty()) {
                throw new IOException("change is forbidden:\n" + forbidden);
            }
        }
        // wipe private keys from diff
        for (Direction direction : sequence.merged.directions.values()) {
            if (direction.priv) {
                result.remove(direction.name);
            }
        }
        valuesFile = sequence.createValuesFile(localSettings.yaml, localSettings.world, values);
        try {
            LOGGER.info("values: " + valuesFile.readString());
            exec(dryrun, kubeContext,
                    localSettings.home, upgrade ? "upgrade" : "install", "--debug", "--values", valuesFile.getAbsolute(), name,
                    toolkit.chart(sequence.merged.chartOpt).reference);
            return result;
        } finally {
            valuesFile.deleteFile();
        }
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
