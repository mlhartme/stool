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
 * See https://docs.docker.com/registry/spec/api/
 * and https://docs.docker.com/registry/deploying/
 */
public class Registry {
    public static Registry create(HttpNode root, String wirelog) {
        if (wirelog != null) {
            HttpFilesystem.wireLog(wirelog);
        }
        return new Registry(root, null);
    }

    private final HttpNode root;

    public Registry(HttpNode root, String token) {
        this.root = root;
        if (token != null) {
            this.root.getRoot().addExtraHeader("Authorization", "Bearer " + token);
        }
    }

    public String login(String realm, String service, String scope) throws IOException {
        HttpNode login;

        login = (HttpNode) root.getWorld().validNode(realm);
        login = login.withParameter("service", service);
        login = login.withParameter("scope", scope);
        return JsonParser.parseString(login.readString()).getAsJsonObject().get("token").getAsString();
    }

    /** @return list of repositories */
    public List<String> catalog() throws IOException {
        final String prefix = "realm=\"";
        JsonObject result;
        StatusException se;
        String auth;
        int start;
        int end;
        String url;

        try {
            result = JsonParser.parseString(root.join("v2/_catalog").readString()).getAsJsonObject();
        } catch (NewInputStreamException e) {
            if (e.getCause() instanceof StatusException) {
                se = (StatusException) e.getCause();
                if (se.getStatusLine().code == 401) {
                    auth = se.getHeaderList().getFirstValue("Www-Authenticate");
                    if (auth != null) {
                        throw new AuthException(get(auth, "realm"), get(auth, "service"), get(auth, "scope"));
                    } else {
                        // fall-through
                    }
                }
            }
            throw e;
        }
        return toList(result.get("repositories").getAsJsonArray());
    }

    private static String get(String header, String arg) throws IOException {
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

    /** @return list of tags */
    public List<String> tags(String repository) throws IOException {
        JsonObject result;

        result = JsonParser.parseString(root.join("v2/" + repository + "/tags/list").readString()).getAsJsonObject();
        return toList(result.get("tags").getAsJsonArray());
    }

    public JsonObject manifest(String repository, String tag) throws IOException {
        HeaderList hl;

        hl = HeaderList.of("Accept", "application/vnd.docker.distribution.manifest.v2+json");
        return JsonParser.parseString(root.join("v2/" + repository + "/manifests/" + tag).withHeaders(hl).readString()).getAsJsonObject();
    }

    /** implementation from https://forums.docker.com/t/retrieve-image-labels-from-manifest/37784/3 */
    public Map<String, String> labels(String repository, String digest) throws IOException {
        return toMap(info(repository, digest).get("container_config").getAsJsonObject().get("Labels").getAsJsonObject());
    }

    public JsonObject info(String repository, String digest) throws IOException {
        return JsonParser.parseString(root.join("v2/" + repository + "/blobs/" + digest).readString()).getAsJsonObject();
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
}
