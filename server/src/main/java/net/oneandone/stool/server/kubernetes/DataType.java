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
package net.oneandone.stool.server.kubernetes;

import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSourceBuilder;
import io.kubernetes.client.openapi.models.V1KeyToPath;
import io.kubernetes.client.openapi.models.V1KeyToPathBuilder;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.openapi.models.V1SecretVolumeSourceBuilder;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeBuilder;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ConfigMap or Secrets */
public class DataType {
    public static DataType configMap(String name, String path) {
        return new DataType(false, name, path);
    }
    public static DataType secrets(String name, String path) {
        return new DataType(true, name, path);
    }

    public final boolean secret;
    public final String name;
    public final String path;
    public final Map<String, String> keyToPaths;
    public final Map<String, String> data;

    private DataType(boolean secret, String name, String path) {
        this.secret = secret;
        this.name = name;
        this.path = path;
        this.keyToPaths = new HashMap<>();
        this.data = new HashMap<>();
    }

    //--

    public void addData(FileNode root, FileNode project) throws IOException {
        for (FileNode file : project.find("**/*")) {
            if (file.isDirectory()) {
                continue;
            }
            data.put(pathToKey(file.getRelative(root)), file.readString());
        }
    }

    public void addKeyToPathMap(FileNode root, FileNode project) throws IOException {
        String relative;

        for (FileNode file : project.find("**/*")) {
            if (file.isDirectory()) {
                continue;
            }
            relative = file.getRelative(root);
            keyToPaths.put(pathToKey(relative), relative);
        }
    }

    private static String pathToKey(String path) {
        return path.replace("/", "_").replace(':', '-');
    }

    //--

    public V1Volume volume(String volumeName) {
        V1SecretVolumeSource ss;
        V1ConfigMapVolumeSource cs;
        List<V1KeyToPath> items;

        if (keyToPaths != null) {
            items = new ArrayList<>();
            for (Map.Entry<String, String> entry : keyToPaths.entrySet()) {
                items.add(new V1KeyToPathBuilder().withKey(entry.getKey()).withPath(entry.getValue()).build());
            }
        } else {
            items = null;
        }
        if (secret) {
            ss = new V1SecretVolumeSourceBuilder().withSecretName(name).withItems(items).build();
            return new V1VolumeBuilder().withName(volumeName).withSecret(ss).build();
        } else {
            cs = new V1ConfigMapVolumeSourceBuilder().withName(name).withItems(items).build();
            return new V1VolumeBuilder().withName(volumeName).withConfigMap(cs).build();
        }
    }

    public V1VolumeMount mount(String volumeName) {
        V1VolumeMount result;

        result = new V1VolumeMount();
        result.setName(volumeName);
        result.setMountPath(path);
        return result;
    }
}
