package net.oneandone.stool.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.kubernetes.api.model.NamedContext;
import io.fabric8.kubernetes.client.Config;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.cli.Caller;
import net.oneandone.stool.cli.Client;
import net.oneandone.stool.cli.Context;
import net.oneandone.stool.cli.Reference;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.util.Json;
import net.oneandone.stool.util.Predicate;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.oneandone.stool.util.Json.string;

/**
 * Global configuration, represents settings.yaml
 */
public class Settings {
    public static Settings load(World world) throws IOException {
        FileNode home;

        home = Settings.home(world);
        return Settings.load(home, Settings.settingsYaml(home));
    }

    public static Settings load(FileNode home, FileNode file) throws IOException {
        ObjectMapper yaml;
        ObjectNode settings;
        Settings result;

        yaml = Json.newYaml();
        try (Reader src = file.newReader()) {
            settings = (ObjectNode) yaml.readTree(src);
        }
        result = new Settings(file.getWorld(), yaml, Json.newJson(), home, settings);
        result.local.validate();
        return result;
    }

    public static Settings create(World world) throws IOException {
        ObjectMapper yaml;
        Settings result;

        yaml = Json.newYaml();
        result = new Settings(world, yaml, Json.newJson(), home(world), yaml.createObjectNode());
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

    public final World world;
    public final ObjectMapper yaml;
    public final ObjectMapper json;

    private String currentContext;


    public final Map<String, Context> proxies;

    public final LocalSettings local;
    public final String stageLogs;

    //--

    public final String loglevel;

    /**
     * public url for kubernetes api -- reported to clients to use temporary service accounts
     */
    public final String kubernetes;

    public Settings(World world, ObjectMapper yaml, ObjectMapper json, FileNode home, ObjectNode settings) {
        ObjectNode localNode;

        this.world = world;
        this.yaml = yaml;
        this.json = json;
        this.currentContext = settings.has("currentContext") ? settings.get("currentContext").asText() : null;
        this.proxies = parseProxies((ArrayNode) settings.get("proxies"));
        localNode = (ObjectNode) settings.get("local");
        if (localNode == null) {
            localNode = yaml.createObjectNode();
        }
        this.local = new LocalSettings(json, home, localNode);
        this.stageLogs = string(localNode, "stageLogs", world.getHome().join(".sc/logs").getAbsolute());
        this.loglevel = Json.string(localNode, "loglevel", "ERROR");
        this.kubernetes = Json.string(localNode, "kubernetes", "http://localhost");
    }

    public Settings(Settings from) throws IOException {
        this.world = World.create();
        this.yaml = Json.newYaml();
        this.json = Json.newJson();
        this.currentContext = from.currentContext;
        this.proxies = new LinkedHashMap<>();
        for (Map.Entry<String, Context> entry : from.proxies.entrySet()) {
            proxies.put(entry.getKey(), entry.getValue().newInstance());
        }
        this.local = new LocalSettings(world, json, from.local);
        this.stageLogs = from.stageLogs;
        this.loglevel = from.loglevel;
        this.kubernetes = from.kubernetes;
    }

    private static Map<String, Context> parseProxies(ArrayNode proxiesOpt) {
        Context context;
        Iterator<JsonNode> iter;
        JsonNode one;
        Map<String, Context> result;

        result = new LinkedHashMap<>();
        if (proxiesOpt != null) {
            iter = proxiesOpt.elements();
            while (iter.hasNext()) {
                one = iter.next();
                context = Context.fromProxyYaml(one);
                result.put(context.name, context);
            }
        }
        return result;
    }

    //-- Stage access

    public List<Stage> list(Engine engine, Predicate predicate, Map<String, IOException> problems) throws IOException {
        List<Stage> result;
        Stage stage;

        result = new ArrayList<>();
        for (String name : list(engine)) {
            try {
                stage = load(engine, name);
            } catch (IOException e) {
                e.printStackTrace();
                problems.put(name, e);
                continue;
            }
            if (predicate.matches(stage)) {
                result.add(stage);
            }
        }
        return result;
    }

    private static List<String> list(Engine engine) throws IOException {
        List<String> result;

        result = engine.helmList();
        result.remove("stool"); // optional, stool server is not required
        return result;
    }

    public List<Stage> listAll(Engine engine) throws IOException {
        List<Stage> result;
        Map<String, IOException> problems;

        problems = new HashMap<>();
        result = list(engine, new Predicate() {
            @Override
            public boolean matches(Stage stage) {
                return true;
            }
        }, problems);
        for (Map.Entry<String, IOException> entry : problems.entrySet()) {
            local.reportException("listAll" /* TODO */, entry.getKey() + ": Session.listAll", entry.getValue());
        }
        return result;
    }

    public Stage load(Engine engine, String name) throws IOException {
        String secretName;
        ObjectNode obj;

        secretName = engine.helmSecretName(name);
        try {
            obj = engine.helmSecretRead(secretName);
        } catch (FileNotFoundException e) {
            throw new StageNotFoundException(name);
        }
        return Stage.create(this, name, obj, Stage.historyFromMap(engine.secretGetAnnotations(secretName)));
    }

    //--

    public FileNode stageLogs(String name) throws MkdirException {
        return world.file(stageLogs).mkdirsOpt().join(name);
    }

    public UserManager createUserManager() throws IOException {
        return UserManager.loadOpt(json, local.lib.join("users.json"));
    }

    //-- contexts

    public Map<String, Context> contexts() {
        Map<String, Context> result;

        result = kubeContexts();
        result.putAll(proxies);
        return result;
    }

    private Map<String, Context> kubeContexts() {
        Config config;
        Map<String, Context> result;
        Context context;

        result = new LinkedHashMap<>();
        config = Config.autoConfigure(null);
        for (NamedContext c : config.getContexts()) {
            context = Context.fromKube(c);
            result.put(context.name, context);
        }
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
        return currentContext().connect(world, this, caller);
    }

    public Reference reference(String str, Settings settings, Caller caller) throws IOException {
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
        return new Reference(context.connect(world, settings, caller), str.substring(0, idx));
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
            SequenceWriter sw = yaml.writerWithDefaultPrettyPrinter().writeValues(dest);
            sw.write(obj);
        }
    }

    public ObjectNode toYaml() {
        ObjectNode obj;
        ObjectNode localNode;
        ArrayNode array;

        obj = yaml.createObjectNode();
        if (currentContext != null) {
            obj.put("currentContext", currentContext);
        }
        localNode = yaml.createObjectNode();
        obj.set("local", localNode);

        localNode.put("stageLog", stageLogs);
        this.local.toYaml(localNode);

        //--

        localNode.put("kubernetes", kubernetes);
        localNode.put("loglevel", loglevel);

        array = obj.putArray("proxies");
        for (Context context : proxies.values()) {
            array.add(context.toObject(yaml));
        }
        return obj;
    }

    //--

    public String toString() {
        ObjectNode obj;

        obj = toYaml();
        try (Writer dest = new StringWriter()) {
            SequenceWriter sw = yaml.writerWithDefaultPrettyPrinter().writeValues(dest);
            sw.write(obj);
            return dest.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
