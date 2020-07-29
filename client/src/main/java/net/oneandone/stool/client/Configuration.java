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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** client configuration */
public class Configuration {
    private final World world;
    private String version;
    private String registryPrefix;
    public final FileNode wirelog;
    public final String clientInvocation;
    public final String clientCommand;
    public final Map<String, Context> contexts;
    private final ObjectMapper yaml;

    public Configuration(World world) {
        this(world, null, null, null);
    }

    public Configuration(World world, FileNode wirelog, String clientInvocation, String clientCommand) {
        this.world = world;
        this.version = null;
        this.registryPrefix = "127.0.0.1:31500/";
        this.contexts = new LinkedHashMap<>();

        // transient
        this.wirelog = wirelog;
        this.clientInvocation = clientInvocation;
        this.clientCommand = clientCommand;

        this.yaml = new ObjectMapper(new YAMLFactory());
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


    //-- contexts

    public Context context() throws IOException {
        for (Context context : contexts.values()) {
            if (context.enabled) {
                return context;
            }
        }
        throw new IOException("context not set");
    }

    public void setContext(String name) {
        for (Context context : contexts.values()) {
            context.enabled = name.equals(context.name);
        }
    }

    public void addContext(String name, boolean enabled, String url, String token) {
        contexts.put(name, new Context(name, url, enabled, token, null, clientInvocation, clientCommand));
    }

    public Context contextLookup(String context) {
        return contexts.get(context);
    }

    public Client contextConnect() throws IOException {
        return context().connect(world);
    }

    public Reference reference(String stageName) throws IOException {
        return new Reference(context().connect(world), stageName);
    }

    public List<Reference> list(String filter) throws IOException {
        Client client;
        List<Reference> result;

        client = contextConnect();
        result = new ArrayList<>();
        result.addAll(Reference.list(client, client.list(filter)));
        return result;
    }

    public void load(FileNode file) throws IOException {
        ObjectNode all;
        Context context;
        Iterator<JsonNode> iter;
        JsonNode one;

        try (Reader src = file.newReader()) {
            all = (ObjectNode) yaml.readTree(src);
        }

        contexts.clear();
        registryPrefix = all.get("registryPrefix").asText();

        iter = all.get("contexts").iterator();
        while (iter.hasNext()) {
            one = iter.next();
            context = Context.fromYaml(one, wirelog, clientInvocation, clientCommand);
            contexts.put(context.name, context);
        }
    }

    public void save(FileNode file) throws IOException {
        ObjectNode obj;
        ArrayNode array;

        obj = yaml.createObjectNode();
        obj.put("registryPrefix", registryPrefix);
        array = obj.putArray("contexts");
        for (Context server : contexts.values()) {
            array.add(server.toYaml(yaml));
        }
        try (Writer dest = file.newWriter()) {
            SequenceWriter sw = yaml.writerWithDefaultPrettyPrinter().writeValues(dest);
            sw.write(obj);
            System.out.println("wrote " + file.readString());
        }
    }
}
