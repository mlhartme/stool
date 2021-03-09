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
import net.oneandone.sushi.fs.World;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Zone {
    public static Zone load(World world, ObjectMapper yaml, Collection<Library> libraries) throws IOException {
        Iterator<JsonNode> directions;
        Zone result;

        result = new Zone();
        result.add(Directions.loadStageDirectionsBase(world, yaml));
        for (Library library : libraries) {
            for (Chart chart : library.charts()) {
                result.add(chart);
            }
            try (Reader src = library.libraryYaml.newReader()) {
                directions = yaml.readTree(src).elements();
            }
            while (directions.hasNext()) {
                result.add(Directions.loadLiteral(result, "builtin", DirectionsRef.BUILDIN, (ObjectNode) directions.next()));
            }
        }
        return result;
    }

    //--

    private final Map<String, Directions> directions;
    private final Map<String, Chart> charts;

    public Zone() {
        this.directions = new HashMap<>();
        this.charts = new HashMap<>();
    }

    public void add(Directions add) throws IOException {
        if (this.directions.put(add.subject, add) != null) {
            throw new IOException("duplicate directions: " + add.subject);
        }
    }

    public void add(Chart chart) throws IOException {
        add(chart.directions);
        if (this.charts.put(chart.name, chart) != null) {
            throw new IOException("duplicate chart: " + chart);
        }
    }

    public int size() {
        return directions.size();
    }

    public Directions directions(String name) throws IOException {
        Directions result;

        result = directions.get(name);
        if (result == null) {
            throw new IOException("directions not found: " + name);
        }
        return result;
    }

    public Chart chart(String name) throws IOException {
        Chart result;

        result = charts.get(name);
        if (result == null) {
            throw new IOException("chart not found: " + name);
        }
        return result;
    }

}
