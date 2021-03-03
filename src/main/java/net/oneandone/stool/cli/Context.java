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
package net.oneandone.stool.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.kubernetes.api.model.NamedContext;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Settings;
import net.oneandone.sushi.fs.World;

import java.io.IOException;

public class Context {
    private static final String KUBE_CONTEXT_PREFIX = "kube-";
    private static final String KUBE_SCHEME = "kube:";


    public static Context fromKube(NamedContext context) {
        String name;

        name = context.getName();
        return new Context(KUBE_CONTEXT_PREFIX + name, KUBE_SCHEME + name, null);
    }

    public static Context fromProxyYaml(JsonNode obj) {
        String token;
        String url;
        String name;

        if (obj.has("token")) {
            token = obj.get("token").asText();
        } else {
            token = null;
        }
        url = obj.get("url").asText();
        if (!url.startsWith("http")) {
            throw new IllegalArgumentException(url);
        }
        name = obj.get("name").asText();
        if (name.startsWith(KUBE_CONTEXT_PREFIX)) {
            throw new ArgumentException("proxy context name may not start with '" + KUBE_CONTEXT_PREFIX + "'");
        }
        return new Context(name, url, token);
    }

    public final String name;
    public final String url;

    /** null to work anonymously */
    public String token;

    public Context(String name, String url, String token) {
        this.name = name;
        this.url = url;
        this.token = token;
    }

    public boolean isKube() {
        return url.startsWith(KUBE_SCHEME);
    }

    public boolean hasToken() {
        return token != null;
    }

    public void auth(World world, ObjectMapper json, Caller caller, String username, String password) throws IOException {
        ProxyClient client;

        if (isKube()) {
            this.token = null;
        } else {
            client = ProxyClient.basicAuth(world, json, name, url, caller, username, password);
            this.token = client.auth();
        }
    }

    public Client connect(World world, Settings settings, Caller caller) throws IOException {
        if (isKube()) {
            return new KubernetesClient(settings.json, name, url.substring(KUBE_SCHEME.length()), settings, caller);
        } else {
            return ProxyClient.token(world, settings.json, name, url, caller, token);
        }
    }

    public ObjectNode toObject(ObjectMapper yaml) {
        ObjectNode result;

        result = yaml.createObjectNode();
        result.put("name", name);
        result.put("url", url);
        if (token != null) {
            result.put("token", token);
        }
        return result;
    }

    public int hashCode() {
        return name.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof Context) {
            return ((Context) obj).name.equals(name);
        } else {
            return false;
        }
    }

    public Context newInstance() {
        return new Context(name, url, token);
    }
}
