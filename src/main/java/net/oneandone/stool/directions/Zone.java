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
import net.oneandone.sushi.fs.file.FileNode;

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
        FileNode file;

        result = new Zone();
        result.add(Directions.loadStageDirectionsBase(world, yaml));
        for (Library library : libraries) {
            for (Chart chart : library.charts()) {
                result.add(Directions.loadChartDirections(yaml, chart));
            }
            file = library.libraryYaml;
            if (file.exists()) {
                try (Reader src = file.newReader()) {
                    directions = yaml.readTree(src).elements();
                }
                while (directions.hasNext()) {
                    result.add(Directions.loadLiteral(result, "builtin", DirectionsRef.BUILDIN, (ObjectNode) directions.next()));
                }
            }
        }
        return result;
    }

    //--

    private final Map<String, Directions> all;

    public Zone() {
        this.all = new HashMap<>();
    }

    public void add(Directions directions) throws IOException {
        if (all.put(directions.subject, directions) != null) {
            throw new IOException("duplicate directions: " + directions.subject);
        }
    }

    public int size() {
        return all.size();
    }

    public Directions directions(String name) throws IOException {
        Directions result;

        result = all.get(name);
        if (result == null) {
            throw new IOException("directions not found: " + name);
        }
        return result;
    }
}
