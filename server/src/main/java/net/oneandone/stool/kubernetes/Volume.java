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
package net.oneandone.stool.kubernetes;

import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;

import java.util.List;

public abstract class Volume {
    public final String name;

    protected Volume(String name) {
        this.name = name;
    }

    public abstract V1Volume volume();
    public abstract void mounts(Mount mount, List<V1VolumeMount> dest);

    public static class Mount {
        public final String path;
        public final boolean subPaths;

        public Mount(String path, boolean subPaths) {
            this.path = path;
            this.subPaths = subPaths;
        }
    }
}
