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
package net.oneandone.stool.registry;

import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.List;

/**
 * Docker Registry API https://docs.docker.com/registry/spec/api/ and Portus API http://port.us.org/docs/API.html.
 *
 * I didn't find a way to query tag authors with Docker Registry API V2, so I hat to fall back to Portus' API :(
 * I didn't find the official V1 Docs - this was closest: https://tuhrig.de/docker-registry-rest-api/
 */
public abstract class Registry {
    public static Registry create(Engine engine, String url) throws IOException {
        PodInfo info;
        HttpNode node;

        if (("http://" + LOCAL_HOST + "/").equals(url)) {
            info = engine.podProbe("stool-registry");
            if (info == null) {
                throw new IOException("registry not found");
            }
            node = (HttpNode) World.create().validNode("http://" + info.ip + ":5000");
            return DockerRegistry.create(node, null);
        } else {
            return PortusRegistry.portus(World.create(), Strings.removeRight(url, "/"), null);
        }
    }

    public static final String LOCAL_HOST = "127.0.0.1:31500";

    protected Registry() {
    }

    /** @return list of repositories */
    public abstract List<String> catalog() throws IOException;
    public abstract List<String> tags(String repository) throws IOException;

    public abstract TagInfo info(PodInfo pod) throws IOException;
    public abstract TagInfo info(String repository, String tag) throws IOException;
    public abstract void deleteRepository(String repository) throws IOException;
}
