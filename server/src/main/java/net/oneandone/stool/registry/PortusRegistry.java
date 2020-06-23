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
import net.oneandone.stool.docker.Daemon;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.sushi.fs.NewInputStreamException;
import net.oneandone.sushi.fs.NodeInstantiationException;
import net.oneandone.sushi.fs.World;
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
public class PortusRegistry extends Registry {
    public static PortusRegistry portus(World world, String portus, String wirelog) throws NodeInstantiationException {
        HttpNode root;
        String ui;
        int idx;
        String username;
        String password;

        root = (HttpNode) world.validNode(portus);
        ui = root.getRoot().getUserInfo();
        if (ui == null) {
            throw new IllegalArgumentException("missing credentials: " + portus);
        }
        root.getRoot().setCredentials(null, null);
        root = (HttpNode) world.node(root.getUri().resolve("/")); // TODO: work-around for sushi bug: does not reset auth header
        idx = ui.indexOf(':');
        username = ui.substring(0, idx);
        password = ui.substring(idx + 1);
        // auth for portus api; in contrast to registry api auth, portus auth can be added upfront
        root.getRoot().addExtraHeader("Portus-Auth", username + ":" + password);

        return doCreate(root, root.getRoot().getHostname(), true, username, password, wirelog);
    }

    public static PortusRegistry doCreate(HttpNode root, String host, boolean portus, String username, String password, String wirelog) {
        if (wirelog != null) {
            HttpFilesystem.wireLog(wirelog);
        }
        return new PortusRegistry(host, portus, username, password, root);
    }

    private final String host;
    private final boolean portus;
    private final String username;
    private final String password;
    private final HttpNode root;

    private String authRepository;
    private String authToken;

    private PortusRegistry(String host, boolean portus, String username, String password, HttpNode root) {
        if (host.contains("/")) {
            throw new IllegalArgumentException(host);
        }
        this.host = host;
        this.portus = portus;
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
        List<String> result;
        String id;

        if (portus) {
            result = new ArrayList<>();
            id = portusRepositoryIdOpt(repository);
            if (id != null) {
                for (JsonElement element : portusTags(id)) {
                    result.add(element.getAsJsonObject().get("name").getAsString());
                }
            }
            return result;
        } else {
            return simpleTags(repository);
        }
    }

    /** @return list of tags */
    public List<String> simpleTags(String repository) throws IOException {
        JsonObject result;
        JsonElement tags;

        result = getJsonObject(repositoryAuth(repository, root.join("v2/" + repository + "/tags/list")));
        tags = result.get("tags");
        return tags.isJsonNull() ? new ArrayList<>() : toList(tags.getAsJsonArray());
    }

    private HttpNode repositoryAuth(String repository, HttpNode node) throws IOException {
        String realm;
        String service;
        HttpNode login;
        HeaderList hl;

        if (!portus) {
            return node;
        }
        if (authRepository == null || !authRepository.equals(repository)) {
            // auth for docker registry api
            try {
                DockerRegistry.local(root).catalog();
                throw new IllegalStateException(root.getUri().toString());
            } catch (AuthException e) {
                realm = e.realm;
                service = e.service;
                // fall-through
            }
            login = (HttpNode) root.getWorld().validNode(realm);
            login.getRoot().setCredentials(username, password);
            try {
                login = login.withParameter("service", service);
                login = login.withParameter("scope", "repository:" + repository + ":*");
                authRepository = repository;
                authToken = getJsonObject(login).get("token").getAsString();
            } finally {
                login.getRoot().setCredentials(null, null);
            }
        }
        hl = HeaderList.of("Authorization", "Bearer " + authToken);
        return node.withHeaders(hl);
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
        JsonObject obj;
        String author;
        LocalDateTime created;
        Map<String, String> labels;

        manifest = manifest(repository, tag);
        digest = manifest.get("config").getAsJsonObject().get("digest").getAsString();
        info = getJsonObject(repositoryAuth(repository, root.join("v2/" + repository + "/blobs/" + digest)));
        if (portus) {
            obj = portusTag(portusRepositoryId(repository), tag);
            created = LocalDateTime.parse(obj.get("created_at").getAsString(), Daemon.DATE_FORMAT);
            author = obj.get("author").getAsJsonObject().get("name").getAsString();
        } else {
            // TODO
            created = null;
            author = null;
        }
        labels = toMap(info.get("container_config").getAsJsonObject().get("Labels").getAsJsonObject());
        return TagInfo.create(digest, host + "/" + repository + ":" + tag, tag, author, created, labels);
    }

    public void deleteRepository(String repository) throws IOException {
        String id;

        if (portus) {
            portusDelete(repository);
        } else {
            for (String tag : tags(repository)) {
                id = info(repository, tag).id;
                deleteTagByDigest(repository, id);
            }
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

    //--

    private JsonArray portusRepositories() throws IOException {
        return getJson(root.join("api/v1/repositories")).getAsJsonArray();
    }

    private String portusRepositoryId(String repository) throws IOException {
        String result;

        result = portusRepositoryIdOpt(repository);
        if (result == null) {
            throw new IOException("repository not found: " + repository);
        }
        return result;
    }

    private String portusRepositoryIdOpt(String repository) throws IOException {
        JsonObject obj;

        for (JsonElement element : portusRepositories()) {
            obj = element.getAsJsonObject();
            if (repository.equals(obj.get("full_name").getAsString())) {
                return obj.get("id").getAsString();
            }
        }
        return null;
    }

    public JsonArray portusTags(String repositoryId) throws IOException {
        return getJson(root.join("api/v1/repositories/" + repositoryId + "/tags")).getAsJsonArray();
    }

    public JsonObject portusTag(String portusRepositoryId, String tag) throws IOException {
        JsonObject result;

        for (JsonElement element : portusTags(portusRepositoryId)) {
            result = element.getAsJsonObject();
            if (tag.equals(result.get("name").getAsString())) {
                return result;
            }
        }
        throw new IOException("tag not found: " + tag);
    }

    public void portusDelete(String repository) throws IOException {
        Method.delete(root.join("api/v1/repositories").join(portusRepositoryId(repository)));
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
