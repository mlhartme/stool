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

import java.io.IOException;
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
public class Registry {
    public static Registry login(HttpNode root, String realm, String service, String scope,
                                 String username, String password) throws IOException {
        HttpNode login;
        String token;

        // auth for portus api
        if (username != null) {
            root.getRoot().addExtraHeader("Portus-Auth", username + ":" + password);
        }

        // auth for docker registry api
        login = (HttpNode) root.getWorld().validNode(realm);
        if (username != null) {
            login.getRoot().setCredentials(username, password);
        }
        login = login.withParameter("service", service);
        login = login.withParameter("scope", scope);
        token = getJsonObject(login).get("token").getAsString();
        root.getRoot().addExtraHeader("Authorization", "Bearer " + token);

        return new Registry(root);
    }

    public static Registry create(HttpNode root, String wirelog) {
        if (wirelog != null) {
            HttpFilesystem.wireLog(wirelog);
        }
        return new Registry(root);
    }

    private final HttpNode root;

    public Registry(HttpNode root) {
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

    public JsonArray portusTags(String repository) throws IOException {
        return getJson(root.join("api/v1/repositories/" + repository + "/tags")).getAsJsonArray();
    }

    public JsonObject manifest(String repository, String tag) throws IOException {
        HeaderList hl;

        hl = HeaderList.of("Accept", "application/vnd.docker.distribution.manifest.v2+json");
        return getJsonObject(root.join("v2/" + repository + "/manifests/" + tag).withHeaders(hl));
    }

    /** implementation from https://forums.docker.com/t/retrieve-image-labels-from-manifest/37784/3 */
    public Map<String, String> labels(String repository, String digest) throws IOException {
        return toMap(info(repository, digest).get("container_config").getAsJsonObject().get("Labels").getAsJsonObject());
    }

    public JsonObject info(String repository, String digest) throws IOException {
        return getJsonObject(root.join("v2/" + repository + "/blobs/" + digest));
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

    public static JsonObject getJsonObject(HttpNode node) throws IOException {
        return getJson(node).getAsJsonObject();
    }

    public static JsonElement getJson(HttpNode node) throws IOException {
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
