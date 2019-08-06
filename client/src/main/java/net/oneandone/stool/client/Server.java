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
package net.oneandone.stool.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Server {
    public static Server fromJson(JsonObject obj, FileNode wirelog, String clientInvocation, String clientCommand) {
        JsonElement token;
        JsonElement enabled;

        token = obj.get("token");
        enabled = obj.get("enabled");
        return new Server(obj.get("name").getAsString(), enabled == null || "true".equals(enabled.getAsString()),
                obj.get("url").getAsString(), token == null ? null : token.getAsString(),
                wirelog, clientInvocation, clientCommand);
    }

    public final String name;
    public final boolean enabled;
    public final String url;

    /** null to work anonymously */
    public String token;

    private volatile FileNode wirelog;
    private volatile String clientInvocation;
    private volatile String clientCommand;

    public Server(String name, boolean enabled, String url, String token, FileNode wirelog, String clientInvocation, String clientCommand) {
        this.name = name;
        this.enabled = enabled;
        this.url = url;
        this.token = token;

        this.wirelog = wirelog;
        this.clientInvocation = clientInvocation;
        this.clientCommand = clientCommand;
    }

    public Server withEnabled(boolean withEnabled) {
        return new Server(name, withEnabled, url, token, wirelog, clientInvocation, clientCommand);
    }

    /** looks weired, but this method allows we to keep the token field private */
    public void addTo(ServerManager dest) {
        dest.add(name, enabled, url, token);
    }

    public boolean hasToken() {
        return token != null;
    }
    public void auth(World world, String username, String password) throws IOException {
        Client client;

        client = Client.basicAuth(world, name, url, wirelog, clientInvocation, clientCommand, username, password);
        this.token = client.auth();
    }

    public Client connect(World world) throws IOException {
        return Client.token(world, name, url, wirelog, clientInvocation, clientCommand, token);
    }

    public JsonObject toJson() {
        JsonObject result;

        result = new JsonObject();
        result.add("name", new JsonPrimitive(name));
        result.add("enabled", new JsonPrimitive(enabled));
        result.add("url", new JsonPrimitive(url));
        if (token != null) {
            result.add("token", new JsonPrimitive(token));
        }
        return result;
    }

}
