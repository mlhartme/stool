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
package net.oneandone.stool.core;

import net.oneandone.stool.helmclasses.Helm;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.util.Json;
import net.oneandone.stool.util.Secrets;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HelmIT {
    @Test
    public void applicationRef() throws IOException {
        World world;
        PortusRegistry portus;
        FileNode root;
        FileNode chart;

        world = World.create();
        root = world.getTemp().createTempDirectory();
        portus = PortusRegistry.create(Json.newJson(), world, Secrets.load(world).portus.resolve("/").toString(), null);
        chart = Helm.resolveRepositoryChart(null, portus, "contargo.server.lan/cisoops-public/charts/kutter", root); // TODO
        assertTrue(chart.isDirectory());
        assertEquals(root.getAbsolute(), chart.getParent().getAbsolute());
    }
}
