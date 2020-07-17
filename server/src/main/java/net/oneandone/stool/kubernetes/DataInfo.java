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

import io.kubernetes.client.openapi.models.V1ObjectMeta;

import java.util.Collections;
import java.util.Map;

/** Represents a pod as used by Stool. In particular, the pod has a single container */
public class DataInfo {
    public static DataInfo create(V1ObjectMeta meta) {
        Map<String, String> labels;

        labels = meta.getLabels();
        return new DataInfo(meta.getName(), labels == null ? Collections.emptyMap() : labels);
    }

    public final String name;
    public final Map<String, String> labels;

    public DataInfo(String name, Map<String, String> labels) {
        this.name = name;
        this.labels = labels;
    }
}
