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
package net.oneandone.stool.docker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Docker Registry API https://docs.docker.com/registry/spec/api/ and Portus API http://port.us.org/docs/API.html.
 *
 * I didn't find a way to query tag authors with Docker Registry API V2, so I hat to fall back to Portus' API :(
 * I didn't find the official V1 Docs - this was closest: https://tuhrig.de/docker-registry-rest-api/
 */
public class Registry {
    public static Registry portus(HttpNode root, String realm, String service, String scope,
                                 String username, String applicationToken, String wirelog) throws IOException {
        HttpNode login;
        String token;

        if (username == null) {
            throw new IllegalArgumentException();
        }
        if (applicationToken == null) {
            throw new IllegalArgumentException();
        }
        // auth for portus api
        root.getRoot().addExtraHeader("Portus-Auth", username + ":" + applicationToken);

        // auth for docker registry api
        login = (HttpNode) root.getWorld().validNode(realm);
        login.getRoot().setCredentials(username, applicationToken);
        login = login.withParameter("service", service);
        login = login.withParameter("scope", scope);
        token = getJsonObject(login).get("token").getAsString();
        root.getRoot().addExtraHeader("Authorization", "Bearer " + token);

        return doCreate(true, root, wirelog);
    }

    public static Registry create(HttpNode root) {
        return create(root, null);
    }

    public static Registry create(HttpNode root, String wirelog) {
        return doCreate(false, root, wirelog);
    }

    public static Registry doCreate(boolean portus, HttpNode root, String wirelog) {
        if (wirelog != null) {
            HttpFilesystem.wireLog(wirelog);
        }
        return new Registry(portus, root);
    }

    private final boolean portus;
    private final HttpNode root;

    private Registry(boolean portus, HttpNode root) {
        this.portus = portus;
        this.root = root;
    }

    /** @return list of repositories */
    public List<String> catalog() throws IOException {
        JsonObject result;

        result = getJsonObject(root.join("v2/_catalog"));
        return toList(result.get("repositories").getAsJsonArray());
    }

    /** @return list of tags */
    public List<String> tags(String repository) throws IOException {
        JsonObject result;

        result = getJsonObject(root.join("v2/" + repository + "/tags/list"));
        return toList(result.get("tags").getAsJsonArray());
    }

    public JsonArray portusRepositories() throws IOException {
        return getJson(root.join("api/v1/repositories")).getAsJsonArray();
    }

    public String portusRepositoryId(String repository) throws IOException {
        JsonObject obj;

        for (JsonElement element : portusRepositories()) {
            obj = element.getAsJsonObject();
            if (repository.equals(obj.get("full_name").getAsString())) {
                return obj.get("id").getAsString();
            }
        }
        throw new IOException("repository not found: " + repository);
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


    /** implementation from https://forums.docker.com/t/retrieve-image-labels-from-manifest/37784/3 */
    public ImageInfo info(String repository, String tag) throws IOException {
        JsonObject manifest;
        String digest;
        JsonObject info;
        JsonObject obj;
        String author;
        LocalDateTime created;

        manifest = manifest(repository, tag);
        digest = manifest.get("config").getAsJsonObject().get("digest").getAsString();
        info = getJsonObject(root.join("v2/" + repository + "/blobs/" + digest));
        if (portus) {
            obj = portusTag(portusRepositoryId(repository), tag);
            created = LocalDateTime.parse(obj.get("created_at").getAsString(), Daemon.DATE_FORMAT);
            author = obj.get("author").getAsJsonObject().get("name").getAsString();
        } else {
            created = null;
            author = null;
        }
        // TODO: strip "sha:" prefix from image id?
        return new ImageInfo(digest, Strings.toList("127.0.0.1:31500/" /* TODO */ + repository + ":" + tag), created, author,
                toMap(info.get("container_config").getAsJsonObject().get("Labels").getAsJsonObject()));
    }

    private JsonObject manifest(String repository, String tag) throws IOException {
        HeaderList hl;

        hl = HeaderList.of("Accept", "application/vnd.docker.distribution.manifest.v2+json");
        return getJsonObject(root.join("v2/" + repository + "/manifests/" + tag).withHeaders(hl));
    }

    public void delete(String repository, String digest) throws IOException {
        try {
            Method.delete(root.join("v2/" + repository + "/manifests/" + digest));
        } catch (StatusException e) {
            if (e.getStatusLine().code == 202) {
                // TODO
                return;
            } else {
                throw e;
            }
        }
    }

    //--

    /** @return image ids mapped to ImageInfo */
    public Map<String, ImageInfo> imageList() throws IOException {
        return imageList(Collections.emptyMap());
    }

    // TODO: performance, caching
    public Map<String, ImageInfo> imageList(Map<String, String> labels) throws IOException {
        ImageInfo info;
        Map<String, ImageInfo> result;

        result = new HashMap<>();
        for (String repository : catalog()) {
            for (String tag : tags(repository)) {
                info = info(repository, tag);
                if (info.matches(labels)) {
                    result.put(info.id, info);
                }
            }
        }
        return result;
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
