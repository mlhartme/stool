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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.stool.util.Json;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.fs.http.StatusException;
import net.oneandone.sushi.fs.http.model.HeaderList;
import net.oneandone.sushi.fs.http.model.Method;

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
        ObjectNode result;

        result = getJsonObject(root.join("v2/_catalog"));
        return toList((ArrayNode) result.get("repositories"));
    }

    /** @return list of tags; empty list if repository does not exist */
    public List<String> tags(String repository) throws IOException {
        ObjectNode result;
        JsonNode tags;

        try {
            result = getJsonObject(root.join("v2/" + repository + "/tags/list"));
        } catch (FileNotFoundException e) {
            return new ArrayList<>();
        }
        tags = result.get("tags");
        return tags.isNull() ? new ArrayList<>() : toList((ArrayNode) tags);
    }

    /** implementation from https://forums.docker.com/t/retrieve-image-labels-from-manifest/37784/3 */
    public TagInfo info(String repository, String tag) throws IOException {
        ObjectNode manifest;
        String digest;
        ObjectNode info;
        String author;
        LocalDateTime created;
        Map<String, String> labels;

        manifest = manifest(repository, tag);
        digest = manifest.get("config").get("digest").asText();
        info = getJsonObject(root.join("v2/" + repository + "/blobs/" + digest));
        // TODO: not available via docker registry api
        created = null;
        author = null;
        labels = Json.stringMap((ObjectNode) info.get("container_config").get("Labels"));
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

    private ObjectNode manifest(String repository, String tag) throws IOException {
        return getJsonObject(withV2Header(root.join("v2/" + repository + "/manifests/" + tag)));
    }

    private HttpNode withV2Header(HttpNode node) {
        HeaderList hl;

        hl = HeaderList.of("Accept", "application/vnd.docker.distribution.manifest.v2+json");
        return node.withHeaders(hl);
    }
}
