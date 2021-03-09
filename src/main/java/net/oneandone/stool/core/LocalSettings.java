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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.stool.Main;
import net.oneandone.stool.directions.Library;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.util.Json;
import net.oneandone.stool.util.Mailer;
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
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.oneandone.stool.util.Json.string;

/** Immutable local settings */
public class LocalSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalSettings.class);

    public final World world;
    public final ObjectMapper yaml;
    public final ObjectMapper json;

    public final Map<String, String> environment;
    public final Map<String, Pair> registryCredentials;
    public final List<String> librarypath;
    public final FileNode lib;

    public final String stageLogs;

    /**
     * used for output and stage urls
     */
    public final String fqdn;

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

    /**
     * public url for kubernetes api -- reported to clients to use temporary service accounts
     */
    public final String kubernetes;

    public static final Separator COLON = Separator.on(":").trim().skipEmpty();

    public LocalSettings(ObjectMapper yaml, ObjectMapper json, FileNode home, ObjectNode local) {
        this.world = home.getWorld();
        this.yaml = yaml;
        this.json = json;

        this.fqdn = Json.string(local, "fqdn", "localhost");
        this.environment = Json.stringMapOpt(local, "environment");
        this.registryCredentials = parseRegistryCredentials(string(local, "registryCredentials", ""));
        this.librarypath = COLON.split(Json.string(local, "librarypath", ""));
        this.lib = home.join("lib");
        this.stageLogs = string(local, "stageLogs", home.getWorld().getHome().join(".sc/logs").getAbsolute());

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
        this.kubernetes = Json.string(local, "kubernetes", "http://localhost");
    }

    public LocalSettings(LocalSettings from) throws IOException {
        this.world = World.create();
        this.yaml = Json.newYaml();
        this.json = Json.newJson();

        this.fqdn = from.fqdn;
        this.environment = new LinkedHashMap<>(from.environment);
        this.registryCredentials = new HashMap<>(from.registryCredentials);
        this.librarypath = new ArrayList<>(from.librarypath);
        this.lib = world.file(from.lib.toPath().toFile());
        this.stageLogs = from.stageLogs;
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
        this.kubernetes = from.kubernetes;
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

    public FileNode stageLogs(String name) throws MkdirException {
        return world.file(stageLogs).mkdirsOpt().join(name);
    }

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

    public ObjectNode toYaml() {
        ObjectNode local;

        local = yaml.createObjectNode();
        local.put("stageLog", stageLogs);
        local.put("fqdn", fqdn);
        local.set("environment", Json.obj(json, environment));
        local.put("registryCredentials", registryCredentialsString());
        local.put("librarypath", COLON.join(librarypath));
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
        local.put("kubernetes", kubernetes);
        return local;
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



    public Map<String, Library> resolvedLibraries() throws IOException {
        FileNode root;
        Map<String, Library> result;
        Library library;

        root = lib.join("libraries").mkdirsOpt();
        result = new LinkedHashMap<>();
        for (String entry : librarypath) {
            if (entry.startsWith("/")) {
                library = Library.fromDirectory(world.file(entry));
            } else {
                library = Library.fromRegistry(createRegistry(entry), entry, root);
            }
            result.put(library.getName(), library);
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

    public UserManager createUserManager() throws IOException {
        return UserManager.loadOpt(json, lib.join("users.json"));
    }


    public Mailer mailer() {
        return new Mailer(mailHost, mailUsername, mailPassword);
    }

    public boolean auth() {
        return !ldapUrl.isEmpty();
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
}
