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
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.server.settings.Settings;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** client configuration */
public class Configuration {
    private final World world;
    private String version;
    public final Map<String, Settings.UsernamePassword> registryCredentials;
    public String charts;
    public String stageLogs;
    public String lib;
    public final FileNode wirelog;
    public final String clientInvocation;
    public final String clientCommand;
    private String currentContext;
    public final Map<String, Context> contexts;
    private final ObjectMapper yaml;

    public Configuration(World world) {
        this(world, null, null, null);
    }

    public Configuration(World world, FileNode wirelog, String clientInvocation, String clientCommand) {
        this.world = world;
        this.version = null;
        this.registryCredentials = new HashMap<>();
        this.currentContext = null;
        this.contexts = new LinkedHashMap<>();
        this.charts = world.getHome().join(".sc/charts").getAbsolute();
        this.stageLogs = world.getHome().join(".sc/logs").getAbsolute();
        this.lib = world.getHome().join(".sc/lib").getAbsolute();

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

    public void setRegistryCredentials(String str) {
        int idx;
        String host;

        registryCredentials.clear();
        for (String entry : Separator.COMMA.split(str)) {
            idx = entry.indexOf('=');
            if (idx < 0) {
                throw new IllegalStateException(entry);
            }
            host = entry.substring(0, idx);
            entry = entry.substring(idx + 1);
            idx = entry.indexOf(':');
            if (idx < 0) {
                throw new IllegalStateException(entry);
            }
            registryCredentials.put(host, new Settings.UsernamePassword(entry.substring(0, idx), entry.substring(idx + 1)));
        }
    }

    public Settings.UsernamePassword registryCredentials(String registry) {
        return registryCredentials.get(registry);
    }

    private String registryCredentialsString() {
        StringBuilder result;

        result = new StringBuilder();
        for (Map.Entry<String, Settings.UsernamePassword> entry : registryCredentials.entrySet()) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(entry.getKey() + "=" + entry.getValue().username + ":" + entry.getValue().password);
        }
        return result.toString();
    }

    //-- contexts

    public Context currentContext() throws IOException {
        Context result;

        result = currentContextOpt();
        if (result == null) {
            throw new IOException("no current context");
        }
        return result;
    }

    public Context currentContextOpt() {
        Context result;

        if (currentContext == null) {
            result = null;
        } else {
            result = contexts.get(currentContext);
            if (result == null) {
                throw new ArgumentException("current context not found: " + currentContext);
            }
        }
        return result;
    }

    public void setCurrentContext(String name) {
        if (contextLookup(name) == null) {
            throw new ArgumentException("no such context: " + name);
        }
        currentContext = name;
    }

    public void addContext(String name, String url, String token) {
        contexts.put(name, new Context(name, url, token, null, clientInvocation, clientCommand));
    }

    public Context contextLookup(String context) {
        return contexts.get(context);
    }

    public Client currentContextConnect() throws IOException {
        return currentContext().connect(world);
    }

    public Reference reference(String str) throws IOException {
        int idx;
        String contextName;
        Context context;

        idx = str.indexOf('@');
        if (idx == -1) {
            throw new ArgumentException("invalid reference: " + str);
        }
        contextName = str.substring(idx + 1);
        context = contextLookup(contextName);
        if (context == null) {
            throw new ArgumentException("context not found: " + str);
        }
        return new Reference(context.connect(world), str.substring(0, idx));
    }

    public List<Reference> list(String filter) throws IOException {
        Client client;
        List<Reference> result;

        client = currentContextConnect();
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
        setRegistryCredentials(string(all, "registryCredentials", ""));
        charts = string(all, "charts", "/etc/charts");
        lib = string(all, "lib", "/var/lib/stool");
        stageLogs = string(all, "stageLogs", "/var/log/stool/stages");
        currentContext = all.has("currentContext") ? all.get("currentContext").asText() : null;

        iter = all.get("contexts").iterator();
        while (iter.hasNext()) {
            one = iter.next();
            context = Context.fromYaml(one, wirelog, clientInvocation, clientCommand);
            contexts.put(context.name, context);
        }
    }

    private static String string(ObjectNode node, String field, String dflt) {
        return node.has(field) ? node.get(field).asText() : dflt;
    }

    public void save(FileNode file) throws IOException {
        ObjectNode obj;
        ArrayNode array;

        obj = yaml.createObjectNode();
        if (currentContext != null) {
            obj.put("currentContext", currentContext);
        }
        obj.put("registryCredentials", registryCredentialsString());
        obj.put("charts", charts);
        obj.put("stageLog", stageLogs);
        obj.put("lib", lib);
        array = obj.putArray("contexts");
        for (Context server : contexts.values()) {
            array.add(server.toYaml(yaml));
        }
        try (Writer dest = file.newWriter()) {
            SequenceWriter sw = yaml.writerWithDefaultPrettyPrinter().writeValues(dest);
            sw.write(obj);
        }
    }
}
