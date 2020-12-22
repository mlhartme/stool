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
package net.oneandone.stool.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.cli.Configuration;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.server.Main;
import net.oneandone.stool.server.api.StageNotFoundException;
import net.oneandone.stool.server.settings.Expire;
import net.oneandone.stool.server.settings.Settings;
import net.oneandone.stool.server.settings.adapter.ExpireTypeAdapter;
import net.oneandone.stool.server.settings.adapter.FileNodeTypeAdapter;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.util.Predicate;
import net.oneandone.stool.util.UsernamePassword;
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
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Immutable. */
public class Server {
    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    public static Server createLocal(World world, String context) throws IOException {
        return create(world, context);
    }

    public static Server createCluster(World world) throws IOException {
        return create(world, null);
    }

    private static Server create(World world, String context) throws IOException {
        Configuration configuration;
        String version;
        Settings settings;
        Server server;
        String localhostIp;
        version = Main.versionString(world);
        boolean openShift;
        FileNode lib;
        FileNode logs;

        settings = Settings.load();
        configuration = new Configuration(world);
        configuration.load(world.getHome().join(".sc.yaml")); // TODO
        LOGGER.info("server version: " + Main.versionString(world));
        LOGGER.info("context: " + context);
        LOGGER.info("server auth: " + settings.auth());
        LOGGER.info("server settings: " + settings);
        LOGGER.info("server configuration: " + configuration);
        try (Engine engine = Engine.createClusterOrLocal(context)) {
            openShift = engine.isOpenShift();
            LOGGER.info("OpenShift: " + openShift);
            localhostIp = InetAddress.getByName("localhost").getHostAddress();
            LOGGER.info("localhostIp: " + localhostIp);
            server = new Server(gson(world), version, context, world, openShift, localhostIp, settings, configuration);
            server.validate();
            return server;
        }
    }

    //--

    /** gson is thread-save */
    public final Gson gson;

    public final String version;

    public final String context;

    /** CAUTION: not thread safe! Try to use engine.world instead */
    private final World world;

    public final boolean openShift;

    /** CAUTION: not thread safe! */
    private final FileNode lib;

    /** Logs of stages. CAUTION: not thread safe! */
    private final FileNode stageLogs;

    public final String localhostIp;

    /** used read-only */
    public final Settings settings;

    public final Configuration configuration;

    public final UserManager userManager;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public Server(Gson gson, String version, String context, World world,
                  boolean openShift, String localhostIp, Settings settings, Configuration configuration) throws IOException {
        this.gson = gson;
        this.version = version;
        this.context = context;
        this.world = world;
        this.openShift = openShift;
        this.lib = world.file(configuration.lib).mkdirsOpt();
        this.stageLogs = world.file(configuration.stageLogs).mkdirsOpt();
        this.localhostIp = localhostIp;
        this.settings = settings;
        this.configuration = configuration;
        this.userManager = UserManager.loadOpt(lib.join("users.json"));
    }

    public String stageFqdn(String stage) {
        return stage + "." + settings.fqdn;
    }

    public FileNode getStageLogs(String name) {
        return stageLogs.join(name);
    }

    //-- Stage listings

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

    //-- Stage access

    public Stage load(Engine engine, String name) throws IOException {
        String secretName;
        JsonObject obj;

        secretName = engine.helmSecretName(name);
        try {
            obj = engine.helmSecretRead(secretName);
        } catch (FileNotFoundException e) {
            throw new StageNotFoundException(name);
        }
        return Stage.create(this, name, obj, Stage.historyFromMap(engine.secretGetAnnotations(secretName)));
    }

    //--

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
        up = configuration.registryCredentials(host);
        if (up != null) {
            uri = uri + up.username + ":" + up.password + "@";
        }
        uri = uri + host;
        return PortusRegistry.create(registryWorld, uri, null);
    }

    //--

    /** logs an error for administrators, i.e. the user is not expected to understand/fix this problem. */
    public void reportException(String command, String exceptionContext, Throwable e) {
        String subject;
        StringWriter body;
        PrintWriter writer;

        LOGGER.error("[" + command + "] " + exceptionContext + ": " + e.getMessage(), e);
        if (!settings.admin.isEmpty()) {
            subject = "[stool exception] " + e.getMessage();
            body = new StringWriter();
            body.write("stool: " + Main.versionString(world) + "\n");
            body.write("command: " + command + "\n");
            body.write("context: " + exceptionContext + "\n");
            body.write("user: " + MDC.get("USER") + "\n"); // TODO
            body.write("fqdn: " + settings.fqdn + "\n");
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
                settings.mailer().send(settings.admin, new String[]{settings.admin}, subject, body.toString());
            } catch (MessagingException suppressed) {
                LOGGER.error("cannot send exception email: " + suppressed.getMessage(), suppressed);
            }
        }
    }

    //-- stool properties

    public static Gson gson(World world) {
        return new GsonBuilder()
                .registerTypeAdapter(FileNode.class, new FileNodeTypeAdapter(world))
                .registerTypeAdapter(Expire.class, new ExpireTypeAdapter())
                .disableHtmlEscaping()
                .serializeNulls()
                .excludeFieldsWithModifiers(Modifier.STATIC, Modifier.TRANSIENT)
                .setPrettyPrinting()
                .create();
    }

    public static String majorMinor(String version) {
        int major;
        int minor;

        major = version.indexOf('.');
        minor = version.indexOf('.', major + 1);
        if (minor == -1) {
            throw new IllegalArgumentException(version);
        }
        return version.substring(0, minor);
    }

    //--

    /** @return path to generates directory */
    public FileNode certificate(String certname) throws IOException {
        FileNode script;
        FileNode dir;
        FileNode broken;

        script = lib.join("cert.sh");
        if (!script.isFile()) {
            world.resource("cert-selfsigned.sh").copyFile(script);
            script.setPermissions("rwxr-xr-x");
            lib.join("certs").mkdirsOpt();
        }
        dir = lib.join("certs", certname);
        try {
            LOGGER.info(world.getTemp().exec(script.getAbsolute(), certname, dir.getAbsolute(), settings.fqdn));
        } catch (IOException e) {
            LOGGER.error(script.getAbsolute() + " failed: " + e.getMessage(), e);
            broken = dir.getParent().join(dir.getName() + ".broken");
            broken.deleteTreeOpt();
            dir.move(broken);
            broken.join("error.log").writeString(e.getMessage());
            throw e;
        }
        return dir;
    }

    //--

    public void validate() throws IOException {
        if (settings.auth()) {
            if (settings.ldapSso.isEmpty()) {
                LOGGER.error("ldapSso cannot be empty because security is enabled");
                throw new IOException("ldapSso is empty");
            }
            if (System.getProperty("server.ssl.key-store") == null) {
                LOGGER.error("enable ssl when running authenticated");
                throw new IOException("enable ssl when running authenticated");
            }
        }
        LOGGER.info("server validation ok");
    }

    public void checkFaultPermissions(String user, List<String> projects) throws IOException {
        Properties permissions;
        String lst;

        if (projects.isEmpty()) {
            return;
        }
        permissions = world.file("/etc/fault/workspace.permissions").readProperties();
        for (String project : projects) {
            lst = permissions.getProperty(project);
            if (lst == null) {
                throw new ArgumentException("fault project unknown or not accessible on this host: " + project);
            }
            if (!Separator.COMMA.split(lst).contains(user)) {
                throw new ArgumentException("fault project " + project + ": permission denied for user " + user);
            }
        }
    }
}
