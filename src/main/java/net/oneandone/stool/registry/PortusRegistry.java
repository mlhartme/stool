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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.stool.util.Json;
import net.oneandone.sushi.fs.NewInputStreamException;
import net.oneandone.sushi.fs.NodeInstantiationException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.fs.http.StatusException;
import net.oneandone.sushi.fs.http.model.HeaderList;
import net.oneandone.sushi.fs.http.model.Method;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Registry implementation with Portus API http://port.us.org/docs/API.html.
 */
public class PortusRegistry extends Registry {
    public static PortusRegistry create(World world, String uri, String wirelog) throws NodeInstantiationException {
        HttpNode root;
        String ui;
        int idx;
        String username;
        String password;

        root = (HttpNode) world.validNode(uri);
        ui = root.getRoot().getUserInfo();
        if (ui == null) {
            throw new IllegalArgumentException("missing credentials: " + uri);
        }
        root.getRoot().setCredentials(null, null);
        root = (HttpNode) world.node(root.getUri().resolve("/")); // TODO: work-around for sushi bug: does not reset auth header  TODO: removes path from uri
        idx = ui.indexOf(':');
        username = ui.substring(0, idx);
        password = ui.substring(idx + 1);

        // auth for portus api; in contrast to registry api auth, portus auth can be added upfront
        root.getRoot().addExtraHeader("Portus-Auth", username + ":" + password);

        if (wirelog != null) {
            HttpFilesystem.wireLog(wirelog);
        }
        return new PortusRegistry(root.getRoot().getHostname(), username, password, root);
    }

    private final ObjectMapper json;
    private final String host;
    private final String username;
    private final String password;
    private final HttpNode root;

    private String authRepository;
    private String authToken;

    private PortusRegistry(String host, String username, String password, HttpNode root) {
        if (host.contains("/")) {
            throw new IllegalArgumentException(host);
        }
        this.json = new ObjectMapper();
        this.host = host;
        this.username = username;
        this.password = password;
        this.root = root;

        this.authRepository = null;
        this.authToken = null;
    }

    /** @return list of repositories */
    public List<String> list() throws IOException {
        List<String> result;
        Iterator<JsonNode> iter;

        result = new ArrayList<>();
        iter = portusRepositories().elements();
        while (iter.hasNext()) {
            result.add(iter.next().get("full_name").asText());
        }
        return result;
    }

    /** @return list of tags; empty list if repository does not exist */
    public List<String> tags(String repository) throws IOException {
        List<String> result;
        String id;

        result = new ArrayList<>();
        id = portusRepositoryIdOpt(repository);
        if (id != null) {
            for (JsonNode element : portusTags(id)) {
                result.add(element.get("name").asText());
            }
        }
        return result;
    }

    private HttpNode repositoryAuth(String repository, HttpNode node) throws IOException {
        String realm;
        String service;
        HttpNode login;
        HeaderList hl;

        if (authRepository == null || !authRepository.equals(repository)) {
            // auth for docker registry api
            try {
                DockerRegistry.create(root).list();
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
                authToken = getJsonObject(login).get("token").asText();
            } finally {
                login.getRoot().setCredentials(null, null);
            }
        }
        hl = HeaderList.of("Authorization", "Bearer " + authToken);
        return node.withHeaders(hl);
    }

    /** implementation from https://forums.docker.com/t/retrieve-image-labels-from-manifest/37784/3 */
    public TagInfo info(String repository, String tag) throws IOException {
        ObjectNode manifest;
        String digest;
        ObjectNode info;
        ObjectNode obj;
        String author;
        LocalDateTime created;
        Map<String, String> labels;

        manifest = manifest(repository, tag);
        digest = manifest.get("config").get("digest").asText();
        info = getJsonObject(repositoryAuth(repository, root.join("v2/" + repository + "/blobs/" + digest)));
        obj = portusTag(portusRepositoryId(repository), tag);
        created = LocalDateTime.parse(obj.get("created_at").asText(), Registry.DATE_FORMAT);
        author = obj.get("author").get("name").asText();
        labels = Json.stringMap((ObjectNode) info.get("container_config").get("Labels"));
        return TagInfo.create(digest, host + "/" + repository + ":" + tag, tag, author, created, labels);
    }

    public static class RepositoryNotFoundException extends IOException {
        public RepositoryNotFoundException(String repository) {
            super(repository);
        }
    }

    // TODO: rename to deleteOpt
    public void delete(String repository) throws IOException {
        String id;

        id = portusRepositoryIdOpt(repository);
        if (id != null) {
            Method.delete(root.join("api/v1/repositories").join(id));
        }
        // TODO: return id != null
    }

    //--

    private ArrayNode portusRepositories() throws IOException {
        return (ArrayNode) getJson(root.join("api/v1/repositories"));
    }

    private String portusRepositoryId(String repository) throws IOException {
        String result;

        result = portusRepositoryIdOpt(repository);
        if (result == null) {
            throw new RepositoryNotFoundException(repository);
        }
        return result;
    }

    private String portusRepositoryIdOpt(String repository) throws IOException {
        Iterator<JsonNode> iter;
        ObjectNode obj;

        iter = portusRepositories().elements();
        while (iter.hasNext()) {
            obj = (ObjectNode) iter.next();
            if (repository.equals(obj.get("full_name").asText())) {
                return obj.get("id").asText();
            }
        }
        return null;
    }

    private ArrayNode portusTags(String repositoryId) throws IOException {
        return (ArrayNode) getJson(root.join("api/v1/repositories/" + repositoryId + "/tags"));
    }

    private ObjectNode portusTag(String portusRepositoryId, String tag) throws IOException {
        ObjectNode result;
        Iterator<JsonNode> iter;

        iter = portusTags(portusRepositoryId).elements();
        while (iter.hasNext()) {
            result = (ObjectNode) iter.next();
            if (tag.equals(result.get("name").asText())) {
                return result;
            }
        }
        throw new IOException("tag not found: " + tag);
    }

    private ObjectNode manifest(String repository, String tag) throws IOException {
        return getJsonObject(withV2Header(repositoryAuth(repository, root.join("v2/" + repository + "/manifests/" + tag))));
    }

    private HttpNode withV2Header(HttpNode node) {
        HeaderList hl;

        hl = HeaderList.of("Accept", "application/vnd.docker.distribution.manifest.v2+json");
        return node.withHeaders(hl);
    }
    //--

    private ObjectNode getJsonObject(HttpNode node) throws IOException {
        return (ObjectNode) getJson(node);
    }

    private JsonNode getJson(HttpNode node) throws IOException {
        StatusException se;
        String auth;

        try {
            return json.readTree(node.readString());
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
