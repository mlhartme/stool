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
package net.oneandone.stool.docker;

import com.google.gson.JsonObject;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.http.HttpNode;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class RegistryIT {
    @Test
    public void catalog() throws IOException {
        World world;
        HttpNode root;
        Registry registry;
        JsonObject manifest;
        String digest;

        world = World.create();
        root = (HttpNode) world.validNode("http://localhost:5000");
        registry = new Registry(root);
        assertEquals(Arrays.asList("ba", "foo"), registry.catalog());
        assertEquals(Arrays.asList("latest"), registry.tags("foo"));
        manifest = registry.manifest("foo", "latest");
        digest = manifest.get("config").getAsJsonObject().get("digest").getAsString();
        System.out.println("digest: " + digest);
        registry.delete("foo", digest); // TODO: yields 405 error
        System.out.println("ok");
    }
}
