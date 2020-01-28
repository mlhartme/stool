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
package net.oneandone.stool.server.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.stool.server.Main;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.StageExistsException;
import net.oneandone.stool.server.configuration.Expire;
import net.oneandone.stool.server.docker.BuildError;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.logging.DetailsLogEntry;
import net.oneandone.stool.server.stage.Image;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.users.User;
import net.oneandone.stool.server.util.AppInfo;
import net.oneandone.stool.server.util.Context;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.Ports;
import net.oneandone.stool.server.util.PredicateParser;
import net.oneandone.stool.server.util.Property;
import net.oneandone.stool.server.util.Validation;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final Server server;
    private final String engineLogFile;

    @Autowired
    public ApiController(Server server) {
        this.server = server;
        this.engineLogFile = server.configuration.engineLogFile();
    }

    private Engine engine() throws IOException {
        return Engine.create(engineLogFile);
    }

    @GetMapping("/info")
    public String info() throws IOException {
        JsonObject result;

        try (Engine engine = engine()) {
            result = new JsonObject();
            result.addProperty("version", Main.versionString(engine.world));
            result.addProperty("memory-quota", server.configuration.memoryQuota == 0 ? "" : server.memoryReservedContainers(engine) + "/" + server.configuration.memoryQuota);
            result.addProperty("disk-quota", server.configuration.diskQuota == 0 ? "" : server.diskQuotaReserved(engine) + "/" + server.configuration.diskQuota);
            return result.toString();
        }
    }

    @PostMapping("/auth")
    public String auth() throws IOException {
        User user;
        String result;

        if (server.configuration.ldapUrl.isEmpty()) {
            throw new IOException("authentication is disabled");
        }
        user = User.authenticatedOpt();
        if (user == null) {
            throw new IllegalStateException();
        }
        result = server.userManager.generateToken(user);
        server.userManager.save();
        return new JsonPrimitive(result).toString();
    }


    @GetMapping("/stages")
    public String list(@RequestParam(value = "filter", required = false, defaultValue = "") String filter,
                       @RequestParam(value = "select", required = false, defaultValue = "") String select) throws IOException {
        return select.isEmpty() ? legacyList(filter) : newList(filter, select);
    }

    private String legacyList(String filter) throws IOException {
        JsonArray result;
        Map<String, IOException> problems;
        Context context;

        result = new JsonArray();
        problems = new HashMap<>();
        try (Engine engine = engine()) {
            context = new Context(engine);
            for (Stage stage : server.list(new PredicateParser(context).parse(filter), problems)) {
                result.add(new JsonPrimitive(stage.getName()));
            }
            if (!problems.isEmpty()) {
                throw new IOException("nested problems: " + problems);
            }
            return result.toString();
        }
    }
    private String newList(String filter, String selectStr) throws IOException {
        JsonObject result;
        Context context;
        Map<String, IOException> problems;
        List<String> select;
        JsonObject obj;

        result = new JsonObject();
        problems = new HashMap<>();
        try (Engine engine = engine()) {
            context = new Context(engine);
            for (Stage stage : server.list(new PredicateParser(context).parse(filter), problems)) {
                select = "*".equals(selectStr) ? null : Separator.COMMA.split(selectStr);
                obj = new JsonObject();
                result.add(stage.getName(), obj);
                for (Info info : stage.fields()) {
                    if (select == null || select.remove(info.name())) {
                        obj.add(info.name(), info.getAsJson(context));
                    }
                }
                for (Property property : stage.properties()) {
                    if (select != null && select.remove(property.name())) {
                        obj.add(property.name(), new JsonPrimitive(property.get(context)));
                    }
                }
                if (select != null && !select.isEmpty()) {
                    throw new IOException("select argument: unknown property/field(s): " + select);
                }
            }
            if (!problems.isEmpty()) {
                throw new IOException("nested problems: " + problems);
            }
            return result.toString();
        }
    }

    @PostMapping("/stages/{stage}")
    public void create(@PathVariable("stage") String name, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> config;
        Stage stage;
        Property property;

        try {
            stage = server.create(name);
        } catch (StageExistsException e) {
            response.sendError(409 /* conflict */, "stage exists: " + name);
            return;
        }
        config = map(request, "");
        stage.configuration.expire = Expire.fromNumber(server.configuration.defaultExpire);
        for (Map.Entry<String, String> entry : config.entrySet()) {
            property = stage.propertyOpt(entry.getKey());
            if (property == null) {
                throw new ArgumentException("unknown property: " + entry.getKey());
            }
            property.set(entry.getValue());
        }
        stage.saveConfig();
    }

    @PostMapping("/stages/{stage}/build")
    public String build(@PathVariable("stage") String stage,
                        @RequestParam("comment") String comment,
                        @RequestParam("origin-scm") String originScm,
                        @RequestParam("origin-user") String originUser, @RequestParam("no-cache") boolean noCache,
                        @RequestParam("keep") int keep, InputStream body, HttpServletRequest request) throws Exception {
        Stage.BuildResult result;
        Map<String, String> arguments;
        FileNode war;

        arguments = map(request, "arg.");

        war = null;
        try (Engine engine = engine()) {
            war = engine.world.getTemp().createTempFile();
            war.copyFileFrom(body);
            result = server.load(stage).buildandEatWar(engine, war, comment, originScm, originUser, User.authenticatedOrAnonymous().login, noCache, keep, arguments);
            return buildResult(result.tag, null, result.output).toString();
        } catch (BuildError e) {
            return buildResult(Image.version(e.repositoryTag), e.error, e.output).toString();
        } finally {
            if (war != null && war.exists()) {
                war.deleteFile();
            }
        }
    }

    private JsonObject buildResult(String image, String error, String output) {
        JsonObject result;

        result = new JsonObject();
        result.add("tag", new JsonPrimitive(image));
        if (error != null) {
            result.add("error", new JsonPrimitive(error));
        }
        result.add("output", new JsonPrimitive(output));
        return result;
    }

    @GetMapping("/stages/{stage}/properties")
    public String properties(@PathVariable(value = "stage") String stage) throws IOException {
        JsonObject result;
        Context context;

        result = new JsonObject();
        try (Engine engine = engine()) {
            context = new Context(engine);
            for (Property property : server.load(stage).properties()) {
                result.add(property.name(), new JsonPrimitive(property.get(context)));
            }
            return result.toString();
        }
    }

    @PostMapping("/stages/{stage}/set-properties")
    public String setProperties(@PathVariable(value = "stage") String stageName, HttpServletRequest request) throws IOException {
        Stage stage;
        Property prop;
        String value;
        Map<String, String> arguments;
        JsonObject result;
        Context context;

        stage = server.load(stageName);
        arguments = map(request, "");
        result = new JsonObject();
        try (Engine engine = engine()) {
            context = new Context(engine);
            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                prop = stage.propertyOpt(entry.getKey());
                if (prop == null) {
                    throw new ArgumentException("unknown property: " + entry.getKey());
                }
                value = entry.getValue();
                value = value.replace("{}", prop.get(context));
                try {
                    prop.set(value);
                    result.add(prop.name(), new JsonPrimitive(prop.getAsString(context)));
                } catch (RuntimeException e) {
                    throw new ArgumentException("invalid value for property " + prop.name() + " : " + e.getMessage());
                }
            }
            stage.saveConfig();
            return result.toString();
        }
    }


    @GetMapping("/stages/{stage}/status")
    public String status(@PathVariable(value = "stage") String stage, @RequestParam(value = "select", required = false) String select)
            throws IOException {
        JsonObject result;
        Context context;
        List<String> selection;

        if (select == null || select.isEmpty()) {
            selection = null;
        } else {
            selection = Separator.COMMA.split(select);
        }
        result = new JsonObject();
        try (Engine engine = engine()) {
            context = new Context(engine);
            for (Info info : server.load(stage).fields()) {
                if (selection == null || selection.remove(info.name())) {
                    result.add(info.name(), new JsonPrimitive(info.getAsString(context)));
                }
            }
            if (selection != null && !selection.isEmpty()) {
                throw new ArgumentException("unknown field(s): " + selection);
            }
            return result.toString();
        }
    }

    @PostMapping("/stages/{stage}/validate")
    public String validate(@PathVariable(value = "stage") String stage, @RequestParam("email") boolean email, @RequestParam("repair") boolean repair) throws IOException {
        List<String> output;

        try (Engine engine = engine()) {
            output = new Validation(server, engine).run(stage, email, repair);
        } catch (MessagingException e) {
            throw new IOException("email failure: " + e.getMessage(), e);
        }
        return array(output).toString();
    }

    @GetMapping("/stages/{stage}/appInfo")
    public String appInfo(@PathVariable("stage") String stage) throws Exception {
        try (Engine engine = engine()) {
            return array(new AppInfo(server, engine).run(stage)).toString();
        }
    }


    @PostMapping("/stages/{stage}/start")
    public String start(@PathVariable(value = "stage") String stageName,
                        @RequestParam(value = "http", required = false, defaultValue = "-1") int http,
                        @RequestParam(value = "https", required = false, defaultValue = "-1") int https,
                        HttpServletRequest request) throws IOException {
        Stage stage;
        int global;
        int reserved;
        Map<String, String> environment;
        Map<String, String> apps;

        apps = map(request, "app.");
        environment = map(request, "env.");
        global = server.configuration.diskQuota;
        try (Engine engine = engine()) {
            if (global != 0) {
                reserved = server.diskQuotaReserved(engine);
                if (reserved > global) {
                    throw new IOException("Sum of all stage disk quotas exceeds global limit: " + reserved + " mb > " + global + " mb.\n");
                }
            }

            stage = server.load(stageName);
            stage.checkExpired();
            stage.checkDiskQuota(engine);
            return array(stage.start(engine, server.pool, http, https, environment, apps)).toString();
        }
    }

    @GetMapping("/stages//{stage}/await-startup")
    public String awaitStartup(@PathVariable(value = "stage") String stageName) throws IOException {
        Stage stage;

        System.out.println("await new " + stageName);
        try (Engine engine = engine()) {
            stage = server.load(stageName);
            stage.awaitStartup(engine);

            if (stage.currentOpt(engine) == null) {
                throw new IllegalStateException();
            }
            return Engine.obj(stage.urlMap(engine, server.pool, Stage.APP_NAME)).toString();
        }
    }

    private static JsonArray array(List<String> array) {
        JsonArray result;

        result = new JsonArray(array.size());
        for (String str : array) {
            result.add(new JsonPrimitive(str));
        }
        return result;
    }

    @PostMapping("/stages/{stage}/stop")
    public ResponseEntity<?> stop(@PathVariable(value = "stage") String stage, @RequestParam(value = "apps", required = false, defaultValue = "") String apps) throws IOException {
        List<String> result;

        try (Engine engine = engine()) {
            result = server.load(stage).stop(engine, Separator.COMMA.split(apps));
            return new ResponseEntity<>(array(result).toString(), HttpStatus.OK);
        }
    }

    @GetMapping("/stages/{stage}/tunnel")
    public String tunnel(@PathVariable(value = "stage") String stageName, @RequestParam("port") String port) throws IOException {
        Ports ports;
        int mappedPort;
        JsonObject result;
        String privateKey;

        currentWithPermissions(stageName);
        ports = server.pool.stage(stageName).get(Stage.APP_NAME);
        if (ports == null) {
            throw new ArgumentException("stage is not running: " + stageName);
        }
        switch (port) {
            case "jmx":
                mappedPort = ports.jmxmp;
                break;
            case "debug":
                mappedPort = ports.debug;
                break;
            default:
                throw new ArgumentException("unknown port: " + port);
        }
        privateKey = server.sshDirectory.addPort(mappedPort);
        result = new JsonObject();
        result.add("port", new JsonPrimitive(mappedPort));
        result.add("privateKey", new JsonPrimitive(privateKey));
        return result.toString();
    }

    @GetMapping("/stages/{stage}/ssh")
    public String ssh(@PathVariable(value = "stage") String stageName) throws IOException {
        Stage.Current current;
        String privateKey;

        current = currentWithPermissions(stageName);
        privateKey = server.sshDirectory.addExec(current.container.id);
        return new JsonPrimitive(privateKey).toString();
    }

    private Stage.Current currentWithPermissions(String stageName) throws IOException {
        Stage stage;
        Stage.Current current;

        stage = server.load(stageName);
        try (Engine engine = engine()) {
            current = stage.currentOpt(engine);
        }
        if (current == null || current.container == null) {
            throw new ArgumentException("stage is not running: " + stageName);
        }
        server.checkFaultPermissions(User.authenticatedOrAnonymous().login, current.image.faultProjects);
        return current;
    }

    @ExceptionHandler({ ArgumentException.class })
    public void handleException(ArgumentException e, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        try (PrintWriter dest = response.getWriter()) {
            dest.println(e.getMessage());
        }
    }

    @ExceptionHandler({ StageNotFoundException.class })
    public void handleException(StageNotFoundException e, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        try (PrintWriter dest = response.getWriter()) {
            dest.println(e.getMessage());
        }
    }


    @GetMapping("/stages/{stage}/history")
    public String history(@PathVariable(value = "stage") String stage,
                          @RequestParam("details") boolean details, @RequestParam("max") int max) throws IOException {
        List<AccessLogEntry> entries;
        JsonArray result;

        result = new JsonArray();
        entries = server.load(stage).accessLogAll(max);
        for (AccessLogEntry entry : entries) {
            result.add("[" + AccessLogEntry.DATE_FMT.format(entry.dateTime) + " " + entry.user + "] " + entry.clientCommand);
            if (details) {
                for (DetailsLogEntry detail : server.detailsLog(entry.clientInvocation)) {
                    result.add(new JsonPrimitive("  " + detail.level + " " + detail.message));
                }
            }
        }
        return result.toString();
    }

    @PostMapping("/stages/{stage}/remove")
    public void remove(@PathVariable(value = "stage") String stage) throws IOException {
        try (Engine engine = engine()) {
            server.load(stage).remove(engine);
        }
    }

    @GetMapping("/stages/{name}/logs")
    public String logs(@PathVariable(value = "name") String stageName) throws Exception {
        JsonArray result;
        FileNode dir;
        Stage stage;

        stage = server.load(stageName);
        dir = stage.logs();
        result = new JsonArray();
        for (FileNode file : stage.logs().find("**/*")) {
            if (!file.isDirectory()) {
                result.add(new JsonPrimitive(file.getRelative(dir)));
            }
        }
        return result.toString();
    }

    @GetMapping(value = "/stages/{name}/logs/**", produces = "text/plain")
    public ResponseEntity<Resource> log(@PathVariable(value = "name") String stageName, HttpServletRequest request) throws Exception {
        Stage stage;
        Resource resource;
        String file;

        file = request.getRequestURI();
        file = Strings.removeLeft(file, request.getContextPath());
        file = Strings.removeLeft(file, "/api/stages/" + stageName + "/logs/");
        stage = server.load(stageName);
        resource = new FileSystemResource(stage.logs().join(file).toPath());
        return new ResponseEntity<>(resource, HttpStatus.OK);
    }

    //--

    private Map<String, String> map(HttpServletRequest request, String prefix) {
        Map<String, String> result;
        Enumeration<String> parameters;
        String parameter;

        result = new HashMap<>();
        parameters = request.getParameterNames();
        while (parameters.hasMoreElements()) {
            parameter = parameters.nextElement();
            if (parameter.startsWith(prefix)) {
                result.put(parameter.substring(prefix.length()), request.getParameter(parameter));
            }
        }
        return result;
    }
}
