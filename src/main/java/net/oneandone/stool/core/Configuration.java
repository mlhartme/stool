package net.oneandone.stool.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.Main;
import net.oneandone.stool.cli.Caller;
import net.oneandone.stool.cli.Client;
import net.oneandone.stool.cli.Context;
import net.oneandone.stool.cli.Reference;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.server.api.StageNotFoundException;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.util.Json;
import net.oneandone.stool.util.Mailer;
import net.oneandone.stool.util.Predicate;
import net.oneandone.stool.util.UsernamePassword;
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

import static net.oneandone.stool.util.Json.file;
import static net.oneandone.stool.util.Json.string;

/**
 * Stool configuration, represents sc.yaml
 */
public class Configuration {
    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    public static Configuration load(World world) throws IOException { // TODO
        Configuration configuration;

        configuration = Configuration.load(Configuration.scYaml(world));
        configuration.validate();
        LOGGER.info("version: " + Main.versionString(world));
        LOGGER.info("configuration: " + configuration);
        return configuration;
    }

    public static Configuration load(FileNode file) throws IOException {
        return Configuration.load(file, file.getWorld().getHome().join(".sc"), null);
    }

    public static Configuration load(FileNode file, FileNode configdir, FileNode wirelog) throws IOException {
        Configuration result;

        result = new Configuration(file.getWorld(), configdir, wirelog);
        result.doLoad(file);
        return result;
    }

    public static FileNode scYaml(World world) {
        String str;

        str = System.getenv("SC_YAML");
        return str != null ? world.file(str) : world.getHome().join(".sc.yaml");
    }

    //--

    private final World world;
    public final Map<String, UsernamePassword> registryCredentials;
    public String charts;
    public String stageLogs;
    public FileNode lib;
    public final FileNode wirelog;
    private String currentContext;
    public final Map<String, Context> contexts;
    private final ObjectMapper yaml;

    //--

    public String loglevel;

    /**
     * used for output and application urls
     */
    public String fqdn;

    /**
     * public url for kubernetes api -- reported to clients to use temporary service accounts
     */
    public String kubernetes;

    /**
     * Name + email. Used for problem reports, feedback emails,
     */
    public String admin;

    public String mailHost;

    public String mailUsername;

    public String mailPassword;

    public String ldapUrl;

    public String ldapPrincipal;

    public String ldapCredentials;

    public String ldapUnit;

    public String ldapSso;

    /**
     * Number of days to wait before removing an expired stage.
     */
    public int autoRemove;

    public int defaultExpire;


    public Configuration(World world) {
        this(world, world.getHome().join(".sc"), null);
    }

    public Configuration(World world, FileNode configdir, FileNode wirelog) {
        this.world = world;
        this.registryCredentials = new HashMap<>();
        this.currentContext = null;
        this.contexts = new LinkedHashMap<>();
        this.charts = world.getHome().join(".sc/charts").getAbsolute();
        this.stageLogs = world.getHome().join(".sc/logs").getAbsolute();
        this.lib = configdir.join("lib");

        // transient
        this.wirelog = wirelog;

        this.yaml = new ObjectMapper(new YAMLFactory());

        //--

        fqdn = "localhost";
        kubernetes = "http://localhost";
        loglevel = "ERROR";
        admin = "";
        autoRemove = -1;
        ldapUrl = "";
        ldapPrincipal = "";
        ldapCredentials = "";
        ldapUnit = "";
        ldapSso = "";
        mailHost = "";
        mailUsername = "";
        mailPassword = "";
        defaultExpire = 0;
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


    public String stageFqdn(String stage) {
        return stage + "." + fqdn;
    }

    public UserManager createUserManager() throws IOException {
        return UserManager.loadOpt(lib.mkdirsOpt().join("users.json"));
    }

    public Registry createRegistry(World registryWorld, String image) throws IOException {
        int idx;
        String host;
        UsernamePassword up;
        String uri;

        idx = image.indexOf('/');
        if (idx == -1) {
            throw new IllegalArgumentException(image);
        }
        host = image.substring(0, idx);
        uri = "https://";
        up = registryCredentials(host);
        if (up != null) {
            uri = uri + up.username + ":" + up.password + "@";
        }
        uri = uri + host;
        return PortusRegistry.create(registryWorld, uri, null);
    }

    public Certificates certificates() {
        return new Certificates(lib, charts);
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
            registryCredentials.put(host, new UsernamePassword(entry.substring(0, idx), entry.substring(idx + 1)));
        }
    }

    public UsernamePassword registryCredentials(String registry) {
        return registryCredentials.get(registry);
    }

    private String registryCredentialsString() {
        StringBuilder result;

        result = new StringBuilder();
        for (Map.Entry<String, UsernamePassword> entry : registryCredentials.entrySet()) {
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
        contexts.put(name, new Context(name, url, token, null));
    }

    public Context contextLookup(String context) {
        return contexts.get(context);
    }

    public Client currentContextConnect(Caller caller) throws IOException {
        return currentContext().connect(world, caller);
    }

    public Reference reference(String str, Caller caller) throws IOException {
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
        return new Reference(context.connect(world, caller), str.substring(0, idx));
    }

    public List<Reference> list(String filter, Caller caller) throws IOException {
        Client client;
        List<Reference> result;

        client = currentContextConnect(caller);
        result = new ArrayList<>();
        result.addAll(Reference.list(client, client.list(filter)));
        return result;
    }

    private void doLoad(FileNode file) throws IOException {
        ObjectNode all;
        Context context;
        Iterator<JsonNode> iter;
        JsonNode one;

        try (Reader src = file.newReader()) {
            all = (ObjectNode) yaml.readTree(src);
        }

        contexts.clear();
        setRegistryCredentials(string(all, "registryCredentials", ""));
        charts = string(all, "charts", charts);
        lib = file(all, file.getParent(), "lib", lib);
        stageLogs = string(all, "stageLogs", stageLogs);
        currentContext = all.has("currentContext") ? all.get("currentContext").asText() : null;

        //--
        fqdn = Json.string(all, "fqdn", fqdn);
        kubernetes = Json.string(all, "kubernetes", kubernetes);
        loglevel = Json.string(all, "loglevel", loglevel);
        admin = Json.string(all, "admin", admin);
        autoRemove = Json.number(all, "autoRemove", autoRemove);
        ldapUrl = Json.string(all, "ldapUrl", ldapUrl);
        ldapPrincipal = Json.string(all, "ldapPrincipal", ldapPrincipal);
        ldapCredentials = Json.string(all, "ldapCredentials", ldapCredentials);
        ldapUnit = Json.string(all, "ldapUnit", ldapUnit);
        ldapSso = Json.string(all, "ldapSso", ldapSso);
        mailHost = Json.string(all, "mailHost", mailHost);
        mailUsername = Json.string(all, "mailUsername", mailUsername);
        mailPassword = Json.string(all, "mailPassword", mailPassword);
        defaultExpire = Json.number(all, "defaultExpire", defaultExpire);

        //--
        iter = all.get("contexts").iterator();
        while (iter.hasNext()) {
            one = iter.next();
            context = Context.fromYaml(one, wirelog);
            contexts.put(context.name, context);
        }
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
        ArrayNode array;

        obj = yaml.createObjectNode();
        if (currentContext != null) {
            obj.put("currentContext", currentContext);
        }
        obj.put("registryCredentials", registryCredentialsString());
        obj.put("charts", charts);
        obj.put("stageLog", stageLogs);
        obj.put("lib", lib.getAbsolute());

        //--

        obj.put("fqdn", fqdn);
        obj.put("kubernetes", kubernetes);
        obj.put("loglevel", loglevel);
        obj.put("admin", admin);
        obj.put("autoRemove", autoRemove);
        obj.put("ldapUrl", ldapUrl);
        obj.put("ldapPrincipal", ldapPrincipal);
        obj.put("ldapCredentials", ldapCredentials);
        obj.put("ldapUnit", ldapUnit);
        obj.put("ldapSso", ldapSso);
        obj.put("mailHost", mailHost);
        obj.put("mailUsername", mailUsername);
        obj.put("mailPassword", mailPassword);
        obj.put("defaultExpire", defaultExpire);

        //--

        array = obj.putArray("contexts");
        for (Context context : contexts.values()) {
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

}
