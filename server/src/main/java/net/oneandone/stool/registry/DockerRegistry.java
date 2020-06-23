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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.stool.docker.AuthException;
import net.oneandone.stool.kubernetes.PodInfo;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Docker Registry API https://docs.docker.com/registry/spec/api/ and Portus API http://port.us.org/docs/API.html.
 *
 * I didn't find a way to query tag authors with Docker Registry API V2, so I hat to fall back to Portus' API :(
 * I didn't find the official V1 Docs - this was closest: https://tuhrig.de/docker-registry-rest-api/
 */
public class DockerRegistry extends Registry {
    public static DockerRegistry local(HttpNode root) {
        return local(root, null);
    }

    public static DockerRegistry local(HttpNode root, String wirelog) {
        return doCreate(root, LOCAL_HOST, null, null, wirelog);
    }

    private static DockerRegistry doCreate(HttpNode root, String host, String username, String password, String wirelog) {
        if (wirelog != null) {
            HttpFilesystem.wireLog(wirelog);
        }
        return new DockerRegistry(host, username, password, root);
    }

    private final String host;
    private final String username;
    private final String password;
    private final HttpNode root;

    private String authRepository;
    private String authToken;

    private DockerRegistry(String host, String username, String password, HttpNode root) {
        if (host.contains("/")) {
            throw new IllegalArgumentException(host);
        }
        this.host = host;
        this.username = username;
        this.password = password;
        this.root = root;

        this.authRepository = null;
        this.authToken = null;
    }

    /** @return list of repositories */
    public List<String> catalog() throws IOException {
        JsonObject result;

        result = getJsonObject(root.join("v2/_catalog"));
        return toList(result.get("repositories").getAsJsonArray());
    }

    /** @return list of tags; empty list if repository does not exist */
    public List<String> tags(String repository) throws IOException {
        JsonObject result;
        JsonElement tags;

        result = getJsonObject(repositoryAuth(repository, root.join("v2/" + repository + "/tags/list")));
        tags = result.get("tags");
        return tags.isJsonNull() ? new ArrayList<>() : toList(tags.getAsJsonArray());
    }

    private HttpNode repositoryAuth(String repository, HttpNode node) throws IOException {
        return node;
    }

    public TagInfo info(PodInfo pod) throws IOException {
        String repository;
        int idx;
        String tag;

        repository = Strings.removeLeft(pod.repositoryTag, host + "/");
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
        info = getJsonObject(repositoryAuth(repository, root.join("v2/" + repository + "/blobs/" + digest)));
        // TODO
        created = null;
        author = null;
        labels = toMap(info.get("container_config").getAsJsonObject().get("Labels").getAsJsonObject());
        return TagInfo.create(digest, host + "/" + repository + ":" + tag, tag, author, created, labels);
    }

    public void deleteRepository(String repository) throws IOException {
        String id;

        for (String tag : tags(repository)) {
            id = info(repository, tag).id;
            deleteTagByDigest(repository, id);
        }
    }

    // TODO: returns 202 and does not actually remove the tag
    public void deleteTagByDigest(String repository, String digest) throws IOException {
        try {
            Method.delete(withV2Header(repositoryAuth(repository, root.join("v2/" + repository + "/manifests/" + digest))));
        } catch (StatusException e) {
            if (e.getStatusLine().code == 202) {
                System.out.println("removed " + e.getStatusLine()); // TODO
                // TODO
                return;
            } else {
                throw e;
            }
        }
    }

    private JsonObject manifest(String repository, String tag) throws IOException {
        return getJsonObject(withV2Header(repositoryAuth(repository, root.join("v2/" + repository + "/manifests/" + tag))));
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

    private static List<String> toList(JsonArray array) {
        List<String> result;

        result = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            result.add(element.getAsString());
        }
        return result;
    }

    private static Map<String, String> toMap(JsonObject object) {
        Map<String, String> result;

        result = new LinkedHashMap<>(object.size());
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    private static String getArgument(String header, String arg) throws IOException {
        int idx;
        int len;
        int end;

        idx = header.indexOf(arg);
        if (idx == -1) {
            throw new IOException("argument '" + arg + "' not found in header: " + header);
        }
        len = arg.length();
        if (header.indexOf("=\"", idx + len) != idx + len) {
            throw new IOException("argument '" + arg + "' not properly quoted: " + header);
        }
        end = header.indexOf('"', idx + len + 2);
        if (end == -1) {
            throw new IOException("argument '" + arg + "' not terminated: " + header);
        }
        return header.substring(idx + len + 2, end);
    }
}
