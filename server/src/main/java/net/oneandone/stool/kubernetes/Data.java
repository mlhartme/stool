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
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Specifies ConfigMap or Secrets (with volumes and mounts). */
public class Data {
    public static Data configMap(String name, String mountPath, boolean subPaths) {
        return new Data(false, name, mountPath, subPaths);
    }
    public static Data secrets(String name, String mountPath) {
        return new Data(true, name, mountPath, false);
    }

    public final boolean secret;
    public final String name;
    public final String mountPath;
    public final boolean subPaths;

    /** key to path */
    private final Map<String, String> keyToPaths;

    /** key to data */
    private final Map<String, byte[]> data;

    private Data(boolean secret, String name, String mountPath, boolean subPaths) {
        this.secret = secret;
        this.name = name;
        this.mountPath = mountPath;
        this.keyToPaths = new HashMap<>();
        this.data = new HashMap<>();
        this.subPaths = subPaths;
    }

    //--

    public void addDirectory(FileNode root, FileNode from) throws IOException {
        for (FileNode file : from.find("**/*")) {
            if (file.isDirectory()) {
                continue;
            }
            add(file.getRelative(root), file.readBytes());
        }
    }

    public void addUtf8(String path, String value) {
        try {
            add(path, value.getBytes("utf8"));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public void add(String path, byte[] value) {
        String key;

        key = pathToKey(path);
        keyToPaths.put(key, path);
        data.put(key, value);
    }

    private static String pathToKey(String path) {
        return path.replace("/", "_").replace(':', '-');
    }

    //--

    public void define(Engine engine) throws IOException {
        if (secret) {
            engine.secretCreate(name, data);
        } else {
            engine.configMapCreateBinary(name, data);
        }
    }

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

    public void mounts(String volumeName, List<V1VolumeMount> dest) {
        V1VolumeMount result;

        if (subPaths) {
            for (String path : keyToPaths.values()) {
                result = new V1VolumeMount();
                result.setName(volumeName);
                result.setMountPath(mountPath + "/" + path);
                result.setSubPath(path);
                dest.add(result);
            }
        } else {
            result = new V1VolumeMount();
            result.setName(volumeName);
            result.setMountPath(mountPath);
            dest.add(result);
        }
    }
}
