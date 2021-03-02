package net.oneandone.stool.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.kubernetes.api.model.NamedContext;
import io.fabric8.kubernetes.client.Config;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.Main;
import net.oneandone.stool.cli.Caller;
import net.oneandone.stool.cli.Client;
import net.oneandone.stool.cli.Context;
import net.oneandone.stool.cli.Reference;
import net.oneandone.stool.classes.Helm;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.util.Json;
import net.oneandone.stool.util.Mailer;
import net.oneandone.stool.util.Predicate;
import net.oneandone.stool.util.Pair;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.mail.MessagingException;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(Settings.class);

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
        result.validate();
        return result;
    }

    public static Settings create(World world) throws IOException {
        ObjectMapper yaml;
        Settings result;

        yaml = Json.newYaml();
        result = new Settings(world, yaml, Json.newJson(), home(world), yaml.createObjectNode());
        result.validate();
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

    public final Map<String, Pair> registryCredentials;
    public final List<String> classpath;
    public final FileNode lib;
    public final String stageLogs;

    //--

    public final Map<String, String> environment;

    public final String loglevel;

    /**
     * used for output and stage urls
     */
    public final String fqdn;

    /**
     * public url for kubernetes api -- reported to clients to use temporary service accounts
     */
    public final String kubernetes;

    /**
     * Name + email. Used for problem reports, feedback emails,
     */
    public final String admin;

    public final String ldapUrl;

    public final String ldapPrincipal;

    public final String ldapCredentials;

    public final String ldapUnit;

    public final String ldapSso;

    public final String mailHost;

    public final String mailUsername;

    public final String mailPassword;

    /**
     * Number of days to wait before removing an expired stage.
     */
    public final int autoRemove;

    public final int defaultExpire;

    public static final Separator COLON = Separator.on(":").trim().skipEmpty();

    public Settings(World world, ObjectMapper yaml, ObjectMapper json, FileNode home, ObjectNode settings) {
        ObjectNode local;

        this.world = world;
        this.yaml = yaml;
        this.json = json;

        this.currentContext = settings.has("currentContext") ? settings.get("currentContext").asText() : null;

        this.proxies = parseProxies((ArrayNode) settings.get("proxies"));

        local = (ObjectNode) settings.get("local");
        if (local == null) {
            local = yaml.createObjectNode();
        }
        this.environment = Json.stringMapOpt(local, "environment");
        this.registryCredentials = parseRegistryCredentials(string(local, "registryCredentials", ""));
        this.lib = home.join("lib");
        this.classpath = COLON.split(Json.string(local, "classpath", ""));
        this.stageLogs = string(local, "stageLogs", world.getHome().join(".sc/logs").getAbsolute());
        this.loglevel = Json.string(local, "loglevel", "ERROR");
        this.fqdn = Json.string(local, "fqdn", "localhost");
        this.kubernetes = Json.string(local, "kubernetes", "http://localhost");
        this.admin = Json.string(local, "admin", "");
        this.ldapUrl = Json.string(local, "ldapUrl", "");
        this.ldapPrincipal = Json.string(local, "ldapPrincipal", "");
        this.ldapCredentials = Json.string(local, "ldapCredentials", "");
        this.ldapUnit = Json.string(local, "ldapUnit", "");
        this.ldapSso = Json.string(local, "ldapSso", "");
        this.mailHost = Json.string(local, "mailHost", "");
        this.mailUsername = Json.string(local, "mailUsername", "");
        this.mailPassword = Json.string(local, "mailPassword", "");
        this.autoRemove = Json.number(local, "autoRemove", -1);
        this.defaultExpire = Json.number(local, "defaultExpire", 0);
    }

    public Settings(Settings from) throws IOException {
        this.world = World.create();
        this.yaml = Json.newYaml();
        this.json = Json.newJson();
        this.environment = new LinkedHashMap<>(from.environment);
        this.currentContext = from.currentContext;
        this.proxies = new LinkedHashMap<>();
        for (Map.Entry<String, Context> entry : from.proxies.entrySet()) {
            proxies.put(entry.getKey(), entry.getValue().newInstance());
        }
        this.registryCredentials = new HashMap<>(from.registryCredentials);
        this.classpath = new ArrayList<>(from.classpath);
        this.lib = world.file(from.lib.toPath().toFile());
        this.stageLogs = from.stageLogs;
        this.loglevel = from.loglevel;
        this.fqdn = from.fqdn;
        this.kubernetes = from.kubernetes;
        this.admin = from.admin;
        this.ldapUrl = from.ldapUrl;
        this.ldapPrincipal = from.ldapPrincipal;
        this.ldapCredentials = from.ldapCredentials;
        this.ldapUnit = from.ldapUnit;
        this.ldapSso = from.ldapSso;
        this.mailHost = from.mailHost;
        this.mailUsername = from.mailUsername;
        this.mailPassword = from.mailPassword;
        this.autoRemove = from.autoRemove;
        this.defaultExpire = from.defaultExpire;
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

    public static Map<String, Pair> parseRegistryCredentials(String str) {
        Map<String, Pair> result;
        int idx;
        String host;

        result = new HashMap<>();
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
            result.put(host, new Pair(entry.substring(0, idx), entry.substring(idx + 1)));
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
            reportException("listAll" /* TODO */, entry.getKey() + ": Session.listAll", entry.getValue());
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

    public void validate() throws IOException {
        if (auth()) {
            if (ldapSso.isEmpty()) {
                throw new IOException("ldapSso is empty");
            }
            if (System.getProperty("server.ssl.key-store") == null) {
                throw new IOException("enable ssl when running authenticated");
            }
        }
    }

    /** logs an error for administrators, i.e. the user is not expected to understand/fix this problem. */
    public void reportException(String command, String exceptionContext, Throwable e) {
        String subject;
        StringWriter body;
        PrintWriter writer;

        LOGGER.error("[" + command + "] " + exceptionContext + ": " + e.getMessage(), e);
        if (!admin.isEmpty()) {
            subject = "[stool exception] " + e.getMessage();
            body = new StringWriter();
            body.write("stool: " + Main.versionString(world) + "\n");
            body.write("command: " + command + "\n");
            body.write("context: " + exceptionContext + "\n");
            body.write("user: " + MDC.get("USER") + "\n"); // TODO
            body.write("fqdn: " + fqdn + "\n");
            writer = new PrintWriter(body);
            while (true) {
                e.printStackTrace(writer);
                e = e.getCause();
                if (e == null) {
                    break;
                }
                body.append("Caused by:\n");
            }
            try {
                mailer().send(admin, new String[] { admin }, subject, body.toString());
            } catch (MessagingException suppressed) {
                LOGGER.error("cannot send exception email: " + suppressed.getMessage(), suppressed);
            }
        }
    }

    public FileNode stageLogs(String name) throws MkdirException {
        return world.file(stageLogs).mkdirsOpt().join(name);
    }

    public UserManager createUserManager() throws IOException {
        return UserManager.loadOpt(json, lib.join("users.json"));
    }

    public PortusRegistry createRegistry(String image) throws IOException {
        int idx;
        String host;
        Pair up;
        String uri;

        idx = image.indexOf('/');
        if (idx == -1) {
            throw new IllegalArgumentException(image);
        }
        host = image.substring(0, idx);
        uri = "https://";
        up = registryCredentials(host);
        if (up != null) {
            uri = uri + up.left + ":" + up.right + "@";
        }
        uri = uri + host;
        return PortusRegistry.create(json, world, uri, null);
    }

    public Pair registryCredentials(String registry) {
        return registryCredentials.get(registry);
    }

    private String registryCredentialsString() {
        StringBuilder result;

        result = new StringBuilder();
        for (Map.Entry<String, Pair> entry : registryCredentials.entrySet()) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(entry.getKey() + "=" + entry.getValue().left + ":" + entry.getValue().right);
        }
        return result.toString();
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
        ObjectNode local;
        ArrayNode array;

        obj = yaml.createObjectNode();
        if (currentContext != null) {
            obj.put("currentContext", currentContext);
        }
        local = yaml.createObjectNode();
        obj.set("local", local);

        local.put("registryCredentials", registryCredentialsString());
        local.put("stageLog", stageLogs);
        local.put("classpath", COLON.join(classpath));

        //--

        local.put("fqdn", fqdn);
        local.put("kubernetes", kubernetes);
        local.set("environment", Json.obj(json, environment));
        local.put("loglevel", loglevel);
        local.put("admin", admin);
        local.put("autoRemove", autoRemove);
        if (auth()) {
            local.put("ldapUrl", ldapUrl);
            local.put("ldapPrincipal", ldapPrincipal);
            local.put("ldapCredentials", ldapCredentials);
            local.put("ldapUnit", ldapUnit);
            local.put("ldapSso", ldapSso);
        }
        local.put("mailHost", mailHost);
        local.put("mailUsername", mailUsername);
        local.put("mailPassword", mailPassword);
        local.put("defaultExpire", defaultExpire);

        //--

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

    public Mailer mailer() {
        return new Mailer(mailHost, mailUsername, mailPassword);
    }

    public boolean auth() {
        return !ldapUrl.isEmpty();
    }

    public Map<String, FileNode> resolvedCharts(String kubeContext) throws IOException {
        FileNode root;
        PortusRegistry portus;
        Map<String, FileNode> result;
        FileNode resolved;

        root = lib.join("charts").mkdirsOpt();
        result = new LinkedHashMap<>();
        for (String entry : classpath) {
            resolved = directoryChartOpt(entry);
            if (resolved == null) {
                portus = createRegistry(entry);
                resolved = Helm.resolveRepositoryChart(kubeContext, portus, entry, root).checkDirectory();
            }
            result.put(resolved.getName(), resolved);
        }
        return result;
    }

    private FileNode directoryChartOpt(String classpathEntry) throws IOException {
        if (classpathEntry.startsWith("/")) {
            return world.file(classpathEntry).checkDirectory();
        } else {
            return null;
        }
    }
}