package net.oneandone.stool.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.cli.Caller;
import net.oneandone.stool.cli.Client;
import net.oneandone.stool.cli.Context;
import net.oneandone.stool.cli.Reference;
import net.oneandone.stool.util.Json;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global configuration, represents settings.yaml
 */
public class Settings {
    public static Settings load(World world, String proxyPrefix) throws IOException {
        FileNode home;

        home = Settings.home(world);
        return Settings.load(home, proxyPrefix, Settings.settingsYaml(home));
    }

    public static Settings load(FileNode home, String proxyPrefix, FileNode file) throws IOException {
        ObjectMapper yaml;
        ObjectNode settings;
        Settings result;

        if (!file.exists()) {
            throw new FileNotFoundException("settings not found: " + file);
        }
        yaml = Json.newYaml();
        try (Reader src = file.newReader()) {
            settings = (ObjectNode) yaml.readTree(src);
        }
        result = new Settings(yaml, Json.newJson(), home, proxyPrefix, settings);
        result.local.validate();
        return result;
    }

    public static Settings create(World world) throws IOException {
        ObjectMapper yaml;
        Settings result;

        yaml = Json.newYaml();
        result = new Settings(yaml, Json.newJson(), home(world), "", yaml.createObjectNode());
        result.local.validate();
        return result;
    }

    public static FileNode home(World world) {
        String str;

        str = System.getenv("SC_HOME");
        return str == null ? world.getHome().join(".sc") : world.file(str);
    }

    public static FileNode settingsYaml(FileNode home) {
        return home.join("settings.yaml");
    }


    //--

    private String currentContext;
    public final String loglevel;

    public final LocalSettings local;
    public final Map<String, Context> proxies;

    public Settings(ObjectMapper yaml, ObjectMapper json, FileNode home, String proxyPrefix, ObjectNode settings) {
        ObjectNode localNode;

        this.loglevel = Json.string(settings, "loglevel", "ERROR");
        this.currentContext = settings.has("currentContext") ? settings.get("currentContext").asText() : null;
        localNode = (ObjectNode) settings.get("local");
        if (localNode == null) {
            localNode = yaml.createObjectNode();
        }
        this.local = new LocalSettings(yaml, json, home, localNode);
        this.proxies = parseProxies(proxyPrefix, (ArrayNode) settings.get("proxies"));
    }

    private static Map<String, Context> parseProxies(String proxyPrefix, ArrayNode proxiesOpt) {
        Context context;
        Iterator<JsonNode> iter;
        JsonNode one;
        Map<String, Context> result;

        result = new LinkedHashMap<>();
        if (proxiesOpt != null) {
            iter = proxiesOpt.elements();
            while (iter.hasNext()) {
                one = iter.next();
                context = Context.fromProxyYaml(proxyPrefix, one);
                result.put(context.name, context);
            }
        }
        return result;
    }

    //-- contexts

    public Map<String, Context> contexts() {
        Map<String, Context> result;

        result = Context.loadKube();
        for (String name : proxies.keySet()) {
            if (result.containsKey(name)) {
                throw new ArgumentException("proxy context name clashes with kubernetes context name: " + name);
            }
        }
        result.putAll(proxies);
        return result;
    }

    public Context currentContext() throws IOException {
        Context result;

        if (currentContext == null) {
            throw new IOException("no current context");
        }
        result = contextLookup(currentContext);
        if (result == null) {
            throw new IOException("current context not found: " + currentContext);
        }
        return result;
    }

    public Context currentContextOptWarn(PrintWriter warn) {
        Context result;

        if (currentContext == null) {
            result = null;
        } else {
            result = contextLookup(currentContext);
            if (result == null) {
                warn.println("current context not found: " + currentContext);
                return null;
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
        proxies.put(name, new Context(name, url, token));
    }

    public Context contextLookup(String context) {
        return contexts().get(context);
    }

    public Client currentContextConnect(Caller caller) throws IOException {
        return currentContext().connect(this.local, caller);
    }

    public Reference reference(String str, LocalSettings localSettings, Caller caller) throws IOException {
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
        return new Reference(context.connect(localSettings, caller), str.substring(0, idx));
    }

    public List<Reference> list(String filter, Caller caller) throws IOException {
        Client client;
        List<Reference> result;

        client = currentContextConnect(caller);
        result = new ArrayList<>();
        result.addAll(Reference.list(client, client.list(filter)));
        return result;
    }

    public void save(FileNode file) throws IOException {
        ObjectNode obj;

        obj = toYaml();
        try (Writer dest = file.newWriter()) {
            SequenceWriter sw = local.yaml.writerWithDefaultPrettyPrinter().writeValues(dest);
            sw.write(obj);
        }
    }

    public ObjectNode toYaml() {
        ObjectNode obj;
        ArrayNode array;
        ObjectNode l;

        obj = local.yaml.createObjectNode();
        obj.put("loglevel", loglevel);
        if (currentContext != null) {
            obj.put("currentContext", currentContext);
        }
        l = local.toYaml();
        if (!l.isEmpty()) {
            obj.set("local", l);
        }
        array = obj.putArray("proxies");
        for (Context context : proxies.values()) {
            array.add(context.toObject(local.yaml));
        }
        return obj;
    }

    //--

    public String toString() {
        ObjectNode obj;

        obj = toYaml();
        try (Writer dest = new StringWriter()) {
            SequenceWriter sw = local.yaml.writerWithDefaultPrettyPrinter().writeValues(dest);
            sw.write(obj);
            return dest.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
