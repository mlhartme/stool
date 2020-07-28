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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Configuration {
    private final World world;
    private String version;
    private String registryPrefix;
    public final FileNode wirelog;
    public final String clientInvocation;
    public final String clientCommand;
    public final Map<String, Server> servers;

    public Configuration(World world) {
        this(world, null, null, null);
    }

    public Configuration(World world, FileNode wirelog, String clientInvocation, String clientCommand) {
        this.world = world;
        this.version = null;
        this.registryPrefix = "127.0.0.1:31500/";
        this.servers = new LinkedHashMap<>();

        // transient
        this.wirelog = wirelog;
        this.clientInvocation = clientInvocation;
        this.clientCommand = clientCommand;
    }

    public void setVersion(String version) {
        this.version = version;
    }
    public String version() {
        return version;
    }

    public void setRegistryPrefix(String str) {
        this.registryPrefix = str;
    }
    public String registryPrefix() {
        if (!registryPrefix.endsWith("/")) {
            throw new IllegalStateException(registryPrefix);
        }
        return registryPrefix;
    }


    //-- servers

    public boolean isEmpty() {
        return servers.isEmpty();
    }

    public void add(String name, boolean enabled, String url, String token) {
        servers.put(name, new Server(name, enabled, url, token, null, clientInvocation, clientCommand));
    }

    public Server context() throws IOException {
        for (Server server : servers.values()) {
            if (server.enabled) {
                return server;
            }
        }
        throw new IOException("context not set");
    }

    public Configuration withContext(String name) {
        Configuration result;
        Server server;

        result = new Configuration(world);
        result.setRegistryPrefix(registryPrefix);
        for (Map.Entry<String, Server> entry : servers.entrySet()) {
            server = entry.getValue();
            result.servers.put(entry.getKey(), server.withEnabled(name.equals(server.name)));
        }
        return result;
    }

    public Reference reference(String stageName) throws IOException {
        return new Reference(context().connect(world), stageName);
    }

    public Server serverLookup(String server) {
        return servers.get(server);
    }

    public Client connectContext() throws IOException {
        return context().connect(world);
    }

    public List<Reference> list(String filter) throws IOException {
        Client client;
        List<Reference> result;

        client = connectContext();
        result = new ArrayList<>();
        result.addAll(Reference.list(client, client.list(filter)));
        return result;
    }

    public void load(FileNode file) throws IOException {
        JsonObject all;
        JsonArray array;
        Server server;

        servers.clear();

        all = JsonParser.parseString(file.readString()).getAsJsonObject();
        registryPrefix = all.get("registryPrefix").getAsString();
        array = all.get("servers").getAsJsonArray();
        for (JsonElement element : array) {
            server = Server.fromJson(element.getAsJsonObject(), wirelog, clientInvocation, clientCommand);
            servers.put(server.name, server);
        }
    }

    public void save(Gson gson, FileNode file) throws IOException {
        JsonArray array;
        JsonObject obj;

        array = new JsonArray();
        for (Server server : servers.values()) {
            array.add(server.toJson());
        }
        obj = new JsonObject();
        obj.add("registryPrefix", new JsonPrimitive(registryPrefix));
        obj.add("servers", array);
        try (Writer writer = file.newWriter()) {
            gson.toJson(obj, writer);
        }
    }

    // TODO
    public boolean needAuthentication() {
        for (Server server : servers.values()) {
            if (server.enabled && server.hasToken()) {
                return true;
            }
        }
        return false;
    }

    public Collection<Server> allServer() {
        return Collections.unmodifiableCollection(servers.values());
    }
}
