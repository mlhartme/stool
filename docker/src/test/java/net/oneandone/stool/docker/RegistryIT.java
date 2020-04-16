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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RegistryIT {
    @Test
    public void turnaround() throws IOException {
        HttpNode root;
        String container;
        Registry registry;
        JsonObject manifest;
        String digest;
        Map<Integer, String> ports;

        try (Daemon docker = Daemon.create(null)) {
            ports = new HashMap<>();
            ports.put(5000, "5000");
            container = docker.containerCreate("registry", "registry:2", null,null, false, null, null, null,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), ports);
            docker.containerStart(container);
            docker.imageTag("registry:2", "localhost:5000/my-registry", "1");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                root = (HttpNode) World.create().validNode("http://localhost:5000");
                registry = new Registry(root);
                assertEquals(Arrays.asList("ba", "foo"), registry.catalog());
                assertEquals(Arrays.asList("latest"), registry.tags("foo"));
                manifest = registry.manifest("foo", "latest");
                digest = manifest.get("config").getAsJsonObject().get("digest").getAsString();
                System.out.println("digest: " + digest);

                //registry.delete("foo", digest); // TODO: yields 405 error

                System.out.println("ok");
            } finally {
                System.out.println("wipe container " + container);
                docker.containerStop(container, 5);
                docker.containerRemove(container);
            }
        }

    }
}
