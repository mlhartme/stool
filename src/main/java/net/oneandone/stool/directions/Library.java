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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.LocalSettings;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.util.Versions;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Library {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalSettings.class);

    //--

    public static Library load(World world, ObjectMapper yaml, FileNode directory, String version) throws IOException {
        Iterator<JsonNode> directions;
        Library result;

        result = new Library(version, directory.join("scripts"));
        for (FileNode chart : directory.join("charts").list()) {
            result.addChart(yaml, chart);
        }
        result.addDirections(Directions.loadStageDirectionsBase(world, yaml, result));
        try (Reader src = directory.join("library.yaml").newReader()) {
            directions = yaml.readTree(src).elements();
        }
        while (directions.hasNext()) {
            result.addDirections(Directions.loadLiteral(result, "builtin", DirectionsRef.BUILDIN, (ObjectNode) directions.next()));
        }
        return result;
    }

    /** @return version */
    public static synchronized String resolve(PortusRegistry registry, String repository, FileNode dest) throws IOException {
        String name;
        List<String> tags;
        String tag;
        String existing;
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
        tagFile = dest.join(".tag");
        if (dest.exists()) {
            existing = tagFile.readString().trim();
            if (!tag.equals(existing)) {
                LOGGER.info("updating library " + name + " " + existing + " -> " + tag);
                dest.deleteTree();
            }
        } else {
            LOGGER.info("loading library " + name + " " + tag);
        }
        if (!dest.exists()) {
            tmp = dest.getWorld().getTemp().createTempDirectory();
            try {
                tmp.exec("oras", "pull", repository + ":" + tag);
                dest.mkdir().exec("tar", "zxf", tmp.join("artifact.tgz").getAbsolute());
                if (!dest.exists()) {
                    throw new IllegalStateException(dest.getAbsolute());
                }
            } finally {
                tmp.deleteTree();
            }
            tagFile.writeString(tag);
        }
        return tag;
    }

    private static List<String> sortTags(List<String> lst) { // TODO: also use for taginfo sorting, that's still based on numbers
        Collections.sort(lst, Versions.CMP);
        return lst;
    }

    //--

    private final Map<String, Directions> directions;
    private final Map<String, Chart> charts;
    private final String version;
    public final FileNode scripts;

    public Library(String version, FileNode scripts) {
        this.directions = new HashMap<>();
        this.charts = new HashMap<>();
        this.version = version;
        this.scripts = scripts;
    }


    // directions

    public void addDirections(Directions add) throws IOException {
        if (this.directions.put(add.subject, add) != null) {
            throw new IOException("duplicate directions: " + add.subject);
        }
    }

    public Directions directions(String name) throws IOException {
        Directions result;

        result = directions.get(name);
        if (result == null) {
            throw new IOException("directions not found: " + name);
        }
        return result;
    }

    public int directionsSize() {
        return directions.size();
    }

    //-- charts

    public Chart chart(String name) throws IOException {
        Chart result;

        result = charts.get(name);
        if (result == null) {
            throw new IOException("chart not found: " + name);
        }
        return result;
    }

    public void addChart(ObjectMapper yaml, FileNode directory) throws IOException {
        String chartName;
        Directions d;

        chartName = directory.getName();
        d = Directions.loadChartDirections(yaml, chartName, version, directory.join("values.yaml"));
        addChart(new Chart(chartName, directory.getAbsolute(), d));
    }

    public void addChart(Chart chart) throws IOException {
        addDirections(chart.directions);
        if (charts.put(chart.name, chart) != null) {
            throw new IOException("duplicate chart: " + chart.name);
        }
    }
}
