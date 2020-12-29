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
import net.oneandone.stool.core.Server;
import net.oneandone.stool.server.api.LocalClient;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Context {
    public static Context fromYaml(JsonNode obj, FileNode wirelog, String clientInvocation, String clientCommand) {
        String token;

        if (obj.has("token")) {
            token = obj.get("token").asText();
        } else {
            token = null;
        }
        return new Context(obj.get("name").asText(), obj.get("url").asText(), token, wirelog, clientInvocation, clientCommand);
    }

    public final String name;
    public final String url;

    /** null to work anonymously */
    public String token;

    private volatile FileNode wirelog;
    private volatile String clientInvocation;
    private volatile String clientCommand;

    public Context(String name, String url, String token, FileNode wirelog, String clientInvocation, String clientCommand) {
        this.name = name;
        this.url = url;
        this.token = token;

        this.wirelog = wirelog;
        this.clientInvocation = clientInvocation;
        this.clientCommand = clientCommand;
    }

    public boolean hasToken() {
        return token != null;
    }

    private static final String LOCAL_PREFIX = "local:";

    public boolean isLocal() {
        return url.startsWith(LOCAL_PREFIX);
    }

    public void auth(World world, String username, String password) throws IOException {
        RemoteClient client;

        if (isLocal()) {
            this.token = null;
        } else {
            client = RemoteClient.basicAuth(world, name, url, wirelog, clientInvocation, clientCommand, username, password);
            this.token = client.auth();
        }
    }

    public Client connect(World world) throws IOException {
        if (isLocal()) {
            return new LocalClient(name, url.substring(LOCAL_PREFIX.length()), Server.create(world));
        } else {
            return RemoteClient.token(world, name, url, wirelog, clientInvocation, clientCommand, token);
        }
    }

    public ObjectNode toYaml(ObjectMapper yaml) {
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
}
