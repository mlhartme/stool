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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Configuration;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.util.Strings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ConfigurationTest {
    private static final World WORLD = World.createMinimal();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    public void hello() throws IOException {
        check(values("a", "1"), chart("a"),
                """
                DIRECTIONS: 'foo'
                CHART: chart
                a: '1'
                """);
    }

    @Test
    public void expressions() throws IOException {
        check(values("a", "", "b", "", "c", "hi", "d", "stage.fqdn"), chart("a", "b", "c", "d"),
                """
                DIRECTIONS: 'first'
                CHART: chart
                a:
                  expr: ""
                b:
                  expr: "="
                c:
                  expr: "=hi"
                d:
                  expr: "stool.fqdn"
                """);
    }

    @Test
    public void extend() throws IOException {
        check(values("one", "1", "two", "2"), chart("one", "two"),
                """
                DIRECTIONS: 'foo'
                EXTENDS: 'base'
                one: "1"
                """,
                """
                DIRECTIONS: 'base'
                CHART: 'chart'
                two: "2"
                """);
    }

    @Test
    public void override() throws IOException {
        check(Strings.toMap("f", "1"), chart("f"),
                """
                DIRECTIONS: 'foo'
                EXTENDS: 'base'
                f: "1"
                """,
                """
                DIRECTIONS: 'base'
                CHART: 'chart'
                f: "2"
                """);
    }

    @Test
    public void extra() throws IOException {
        check(Strings.toMap("v", "2"), chart(),
                """
                DIRECTIONS: 'foo'
                CHART: chart
                v:
                    expr: 2
                    extra: true
                """);
    }



    private static Map<String, String> values(String... args) {
        return Strings.toMap(args);
    }

    private static Chart chart(String ... args) {
        Directions d;

        d = new Directions("chart-directions", "no-origin", "no-author", "chart", "version");
        for (String arg : args) {
            d.addNew(new Direction(arg, ""));
        }
        return new Chart("chart", "noversions", "noref", d);
    }

    private void check(Map<String, String> expected, Chart chart, String... directions) throws IOException {
        Configuration c;

        Toolkit toolkit;
        Directions d;
        Directions first;

        toolkit = new Toolkit("empty", WORLD.getTemp().createTempDirectory());
        toolkit.addChart(chart);
        first = null;
        for (String str : directions) {
            d = directions(str);
            if (first == null) {
                first = d;
            } else {
                toolkit.addDirections(d);
            }
        }
        c = Configuration.create(toolkit, first, Collections.emptyMap());
        assertEquals(expected, c.eval(toolkit, WORLD.getTemp().createTempDirectory(), "stage", "fqdn",
                Collections.emptyMap()));
    }

    @Test
    public void extraValueOverrides() throws IOException {
        try {
            check(values(), chart(),
                    """
                    DIRECTIONS: 'foo'
                    CHART: chart
                    f: 2
                    """);
            fail();
        } catch (ArgumentException e) {
            assertEquals("missing extra modifier for extra direction: f", e.getMessage());
        }
    }

    private static Directions directions(String str) throws IOException {
        return Directions.load((ObjectNode) YAML.readTree(str));
    }
}
