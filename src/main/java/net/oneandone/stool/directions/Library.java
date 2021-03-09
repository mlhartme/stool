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
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.LocalSettings;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.util.Versions;
import net.oneandone.sushi.fs.file.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Library {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalSettings.class);

    public static Library fromDirectory(ObjectMapper yaml, FileNode directory) throws IOException {
        return fromDirectory(yaml, directory, "unknown");
    }

    public static Library fromRegistry(ObjectMapper yaml, PortusRegistry registry, String repository, FileNode exports) throws IOException {
        String name;
        List<String> tags;
        String tag;
        String existing;
        FileNode libraryDir;
        FileNode tagFile;
        FileNode tmp;

        if (repository.contains(":")) {
            throw new ArgumentException("invalid library repository: " + repository);
        }
        name = repository.substring(repository.lastIndexOf('/') + 1);
        tags = sortTags(registry.helmTags(Registry.getRepositoryPath(repository)));
        if (tags.isEmpty()) {
            throw new IOException("no tag for repository " + repository);
        }
        tag = tags.get(tags.size() - 1);
        libraryDir = exports.join(name);
        tagFile = libraryDir.join(".tag");
        if (libraryDir.exists()) {
            existing = tagFile.readString().trim();
            if (!tag.equals(existing)) {
                LOGGER.info("updating library " + name + " " + existing + " -> " + tag);
                libraryDir.deleteTree();
            }
        } else {
            LOGGER.info("loading library " + name + " " + tag);
        }
        if (!libraryDir.exists()) {
            tmp = exports.getWorld().getTemp().createTempDirectory();
            try {
                tmp.exec("oras", "pull", repository + ":" + tag);
                libraryDir.mkdir().exec("tar", "zxf", tmp.join("artifact.tgz").getAbsolute());
                if (!libraryDir.exists()) {
                    throw new IllegalStateException(libraryDir.getAbsolute());
                }
            } finally {
                tmp.deleteTree();
            }
            tagFile.writeString(tag);
        }
        return fromDirectory(yaml, libraryDir, tag);
    }

    private static Library fromDirectory(ObjectMapper yaml, FileNode directory, String version) throws IOException {
        Library result;

        result = new Library(directory.getName(), version, directory.join("library.yaml"));
        for (FileNode chart : directory.join("charts").list()) {
            result.addChart(yaml, chart);
        }
        return result;
    }

    private static List<String> sortTags(List<String> lst) { // TODO: also use for taginfo sorting, that's still based on numbers
        Collections.sort(lst, Versions.CMP);
        return lst;
    }

    //--

    private final String name;
    private final Map<String, Chart> charts;
    private final String version;
    public final FileNode libraryYaml;

    public Library(String name, String version, FileNode libraryYaml) {
        this.name = name;
        this.charts = new HashMap<>();
        this.version = version;
        this.libraryYaml = libraryYaml;
    }

    public void addChart(ObjectMapper yaml, FileNode directory) throws IOException {
        String chartName;
        Directions directions;

        chartName = directory.getName();
        directions = Directions.loadChartDirections(yaml, chartName, version, directory.join("values.yaml"));
        addChart(new Chart(name, directory.getAbsolute(), directions, version));
    }

    public void addChart(Chart chart) throws IOException {
        if (charts.put(chart.name, chart) != null) {
            throw new IOException("duplicate chart: " + chart.name);
        }
    }

    public String getName() {
        return name;
    }

    public List<Chart> charts() throws IOException {
        return new ArrayList<>(charts.values());
    }

    public Chart lookupChart(String chartName) {
        return charts.get(chartName);
    }
}
