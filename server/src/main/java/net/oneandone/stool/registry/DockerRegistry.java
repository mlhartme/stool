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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.stool.docker.AuthException;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.NewInputStreamException;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.fs.http.StatusException;
import net.oneandone.sushi.fs.http.model.HeaderList;
import net.oneandone.sushi.fs.http.model.Method;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registry implementation with Docker Registry V2 API https://docs.docker.com/registry/spec/api/
 * I didn't find any official V1 Docs - this was closest: https://tuhrig.de/docker-registry-rest-api/
 */
public class DockerRegistry extends Registry {
    public static DockerRegistry create(HttpNode root) {
        return create(root, null);
    }

    public static DockerRegistry create(HttpNode root, String wirelog) {
        if (wirelog != null) {
            HttpFilesystem.wireLog(wirelog);
        }
        return new DockerRegistry(root.getRoot().getHostname(), root);
    }

    private final String host;
    private final HttpNode root;

    private DockerRegistry(String host, HttpNode root) {
        if (host.contains("/")) {
            throw new IllegalArgumentException(host);
        }
        this.host = host;
        this.root = root;
    }

    /** @return list of repositories */
    public List<String> list() throws IOException {
        JsonObject result;

        result = getJsonObject(root.join("v2/_catalog"));
        return toList(result.get("repositories").getAsJsonArray());
    }

    /** @return list of tags; empty list if repository does not exist */
    public List<String> tags(String repository) throws IOException {
        JsonObject result;
        JsonElement tags;

        try {
            result = getJsonObject(root.join("v2/" + repository + "/tags/list"));
        } catch (FileNotFoundException e) {
            return new ArrayList<>();
        }
        tags = result.get("tags");
        return tags.isJsonNull() ? new ArrayList<>() : toList(tags.getAsJsonArray());
    }

    public TagInfo info(PodInfo pod, String containerName) throws IOException {
        String repository;
        int idx;
        String tag;

        repository = Strings.removeLeft(pod.repositoryTag(containerName), host + "/");
        idx = repository.indexOf(':');
        if (idx == -1) {
            throw new IllegalStateException(repository);
        }
        tag = repository.substring(idx + 1);
        repository = repository.substring(0, idx);
        return info(repository, tag);
    }

    /** implementation from https://forums.docker.com/t/retrieve-image-labels-from-manifest/37784/3 */
    public TagInfo info(String repository, String tag) throws IOException {
        JsonObject manifest;
        String digest;
        JsonObject info;
        String author;
        LocalDateTime created;
        Map<String, String> labels;

        manifest = manifest(repository, tag);
        digest = manifest.get("config").getAsJsonObject().get("digest").getAsString();
        info = getJsonObject(root.join("v2/" + repository + "/blobs/" + digest));
        // TODO: not available via docker registry api
        created = null;
        author = null;
        labels = toMap(info.get("container_config").getAsJsonObject().get("Labels").getAsJsonObject());
        return TagInfo.create(digest, host + "/" + repository + ":" + tag, tag, author, created, labels);
    }

    public void delete(String repository) throws IOException {
        String id;

        for (String tag : tags(repository)) {
            id = info(repository, tag).id;
            deleteTagByDigest(repository, id);
        }
    }

    // TODO: returns 202 and does not actually remove the tag
    private void deleteTagByDigest(String repository, String digest) throws IOException {
        try {
            Method.delete(withV2Header(root.join("v2/" + repository + "/manifests/" + digest)));
        } catch (StatusException e) {
            if (e.getStatusLine().code == 202) {
                return;
            } else {
                throw e;
            }
        }
    }

    private JsonObject manifest(String repository, String tag) throws IOException {
        return getJsonObject(withV2Header(root.join("v2/" + repository + "/manifests/" + tag)));
    }

    private HttpNode withV2Header(HttpNode node) {
        HeaderList hl;

        hl = HeaderList.of("Accept", "application/vnd.docker.distribution.manifest.v2+json");
        return node.withHeaders(hl);
    }
    //--

    private static JsonObject getJsonObject(HttpNode node) throws IOException {
        return getJson(node).getAsJsonObject();
    }

    private static JsonElement getJson(HttpNode node) throws IOException {
        StatusException se;
        String auth;

        try {
            return JsonParser.parseString(node.readString());
        } catch (NewInputStreamException e) {
            if (e.getCause() instanceof StatusException) {
                se = (StatusException) e.getCause();
                if (se.getStatusLine().code == 401) {
                    auth = se.getHeaderList().getFirstValue("Www-Authenticate");
                    if (auth != null) {
                        throw new AuthException(getArgument(auth, "realm"), getArgument(auth, "service"), getArgument(auth, "scope"));
                    } else {
                        // fall-through
                    }
                }
            }
            throw e;
        }
    }
}
