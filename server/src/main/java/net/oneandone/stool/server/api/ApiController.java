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
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.stool.kubernetes.OpenShift;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.stool.server.Main;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.logging.DetailsLogEntry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.server.Stage;
import net.oneandone.stool.server.users.User;
import net.oneandone.stool.server.util.Context;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.PredicateParser;
import net.oneandone.stool.server.util.Value;
import net.oneandone.stool.server.util.Validation;
import net.oneandone.sushi.fs.World;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final Server server;

    @Autowired
    public ApiController(Server server) {
        this.server = server;
    }

    private Engine engine() throws IOException {
        return Engine.createFromCluster(Server.STOOL_LABELS);
    }

    // TODO: used for readiness probes - becomes expensive if I used quotas ...
    @GetMapping("/info")
    public String version() throws IOException {
        JsonObject result;

        result = new JsonObject();
        result.addProperty("version", Main.versionString(World.create() /* TODO */));
        return result.toString();
    }

    @PostMapping("/auth")
    public String auth() throws IOException {
        User user;
        String result;

        if (server.settings.ldapUrl.isEmpty()) {
            throw new IOException("authentication is disabled");
        }
        user = User.authenticatedOpt();
        if (user == null) {
            throw new IllegalStateException();
        }
        result = server.userManager.generateToken(user);
        return new JsonPrimitive(result).toString();
    }


    @GetMapping("/stages")
    public String list(@RequestParam(value = "filter", required = false, defaultValue = "") String filter,
                       @RequestParam(value = "select", required = false, defaultValue = "") String selectStr) throws IOException {
        JsonObject result;
        Context context;
        Map<String, IOException> problems;
        List<String> select;
        JsonObject obj;

        result = new JsonObject();
        problems = new HashMap<>();
        try (Engine engine = engine()) {
            context = new Context(engine);
            for (Stage stage : server.list(engine, new PredicateParser(context).parse(filter), problems)) {
                select = "*".equals(selectStr) ? null : Separator.COMMA.split(selectStr);
                obj = new JsonObject();
                result.add(stage.getName(), obj);
                for (Info info : stage.fields()) {
                    if (select == null || select.remove(info.name())) {
                        obj.add(info.name(), info.getAsJson(context));
                    }
                }
                for (Value value : stage.values(engine).values()) {
                    if (select != null && select.remove(value.name())) {
                        obj.add(value.name(), new JsonPrimitive(value.get(context)));
                    }
                }
                if (select != null && !select.isEmpty()) {
                    throw new IOException("select argument: unknown value/field(s): " + select);
                }
            }
            if (!problems.isEmpty()) {
                throw new IOException("nested problems: " + problems);
            }
            return result.toString();
        }
    }

    @PostMapping("/stages/{stage}")
    public String create(@PathVariable("stage") String name, @RequestParam(value = "image", required = true) String image,
                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        Stage stage;
        Map<String, String> values;

        values = map(request, "value.");
        try (Engine engine = engine()) {
            try {
                engine.helmRead(name);
                response.sendError(409 /* conflict */, "stage exists: " + name);
                return "";
            } catch (FileNotFoundException e) {
                // OK, fall through
            }

            stage = new Stage(server, name);
            // TODO: no values available yet ...
            //  stage.checkExpired(engine);
            stage.install(false, engine, image, values);
            return Engine.obj(stage.urlMap(engine, new Context(engine).registry(stage))).toString();
        }
    }

    @PostMapping("/stages/{stage}/publish")
    public String publish(@PathVariable(value = "stage") String stageName, String imageOpt, HttpServletRequest request) throws IOException {
        Map<String, String> values;
        String result;
        Stage stage;

        values = map(request, "value.");
        try (Engine engine = engine()) {
            stage = server.load(engine, stageName);
            result = stage.install(true, engine, imageOpt, values);
            stage.awaitAvailable(engine);
            return json(result).toString();
        }
    }

    @PostMapping("/stages/{stage}/delete")
    public void delete(@PathVariable(value = "stage") String stageName) throws IOException {
        try (Engine engine = engine()) {
            server.load(engine, stageName).uninstall(engine);
        }
    }

    @GetMapping("/stages/{stage}/values")
    public String values(@PathVariable(value = "stage") String stage) throws IOException {
        JsonObject result;
        Context context;

        result = new JsonObject();
        try (Engine engine = engine()) {
            context = new Context(engine);
            for (Value value : server.load(engine, stage).values(engine).values()) {
                result.add(value.name(), new JsonPrimitive(disclose(value.name(), value.get(context))));
            }
            return result.toString();
        }
    }

    // TODO: configurable
    private static final List<String> DISCLOSE = Strings.toList("cert", "fault");

    private static String disclose(String name, String value) {
        if (DISCLOSE.contains(name)) {
            return "(undisclosed)";
        } else {
            return value;
        }
    }

    @PostMapping("/stages/{stage}/set-values")
    public String setValues(@PathVariable(value = "stage") String stageName, HttpServletRequest request) throws IOException {
        Stage stage;
        Value prop;
        String value;
        Map<String, String> values;
        Context context;
        Map<String, String> clientValues;
        JsonObject result;

        try (Engine engine = engine()) {
            stage = server.load(engine, stageName);
            values = map(request, "");
            result = new JsonObject();
            context = new Context(engine);
            clientValues = new HashMap<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                prop = stage.value(engine, entry.getKey());
                value = entry.getValue();
                value = value.replace("{}", prop.get(context));
                clientValues.put(entry.getKey(), value);
                result.add(prop.name(), new JsonPrimitive(disclose(prop.name(), value)));
            }
            stage.install(true, engine, Stage.KEEP_IMAGE, clientValues);
            stage.awaitAvailable(engine);
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
            for (Info info : server.load(engine, stage).fields()) {
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

    @GetMapping("/stages/{stage}/images")
    public String images(@PathVariable("stage") String name) throws Exception {
        Stage stage;
        List<TagInfo> all;
        TagInfo tagInfo;
        String marker;
        List<String> result;
        List<String> args;
        Registry registry;

        try (Engine engine = engine()) {
            result = new ArrayList<>();
            stage = server.load(engine, name);

            // TODO
            registry = stage.createRegistry(World.create() /* TODO */, engine);
            all = stage.images(engine, registry);

            tagInfo = stage.tagInfo(engine, registry);
            for (TagInfo image : all) {
                marker = image.repositoryTag.equals(tagInfo.repositoryTag) ? "<==" : "";
                result.add(image.tag + "  " + marker);
                result.add("   id:            " + image.id);
                result.add("   repositoryTag: " + image.repositoryTag);
                result.add("   created-at:    " + image.createdAt);
                result.add("   created-by:    " + image.author);
                result.add("   labels:");
                for (Map.Entry<String, String> labels : image.labels.entrySet()) {
                    result.add("     " + labels.getKey() + "\t: " + labels.getValue());
                }
            }
            return array(result).toString();
        }
    }


    private static JsonElement json(String opt) {
        return opt == null ? JsonNull.INSTANCE : new JsonPrimitive(opt);
    }

    @GetMapping("/stages//{stage}/await-available")
    public String awaitAvailable(@PathVariable(value = "stage") String stageName) throws IOException {
        Stage stage;
        Context context;

        try (Engine engine = engine()) {
            stage = server.load(engine, stageName);
            stage.awaitAvailable(engine);
            context = new Context(engine);
            return Engine.obj(stage.urlMap(engine, context.registry(stage))).toString();
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

    @GetMapping("/stages/{stage}/pod-token")
    public String podToken(@PathVariable(value = "stage") String stageName, int timeout) throws IOException {
        JsonObject result;
        Collection<PodInfo> pods;
        PodInfo pod;
        String id;
        String saName;
        String roleName;
        String bindingName;

        if (timeout > 240) {
            throw new IOException("timeout to big: " + timeout);
        }
        currentWithPermissions(stageName);
        try (Engine engine = engine(); OpenShift os = OpenShift.create()) {
            pods = server.load(engine, stageName).runningPods(engine).values();
            if (pods.isEmpty()) {
                throw new IOException("no pods running for stage: " + stageName);
            }
            pod = pods.iterator().next(); // TODO: how to choose different pod

            id = UUID.randomUUID().toString();
            saName = "sa-" + stageName + "-" + id;
            roleName = "role-" + stageName + "-" + id;
            bindingName = "binding-" + stageName + "-" + id;

            os.createServiceAccount(saName);
            os.createRole(roleName, pod.name);
            os.createBinding(bindingName, saName, roleName);

            result = new JsonObject();
            result.add("server", new JsonPrimitive(server.settings.kubernetes));
            result.add("namespace", new JsonPrimitive(engine.getNamespace()));
            result.add("pod", new JsonPrimitive(pod.name));
            result.add("token", new JsonPrimitive(os.getServiceAccountToken(saName)));

            schedulePodTokenCleanup(saName, roleName, bindingName, timeout);
            return result.toString();
        }
    }

    private void schedulePodTokenCleanup(String saName, String roleName, String bindingName, int timeout) {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
        Runnable cleanup = new Runnable() {
            public void run() {
                try (OpenShift os = OpenShift.create()) {
                    os.deleteServiceAccount(saName);
                    os.deleteRole(roleName);
                    os.deleteBinding(bindingName);
                } catch (IOException e) {
                    e.printStackTrace(); // TODO: proper logging ...
                }
            }
        };
        ex.schedule(cleanup, timeout, TimeUnit.MINUTES);
    }

    private TagInfo currentWithPermissions(String stageName) throws IOException {
        Stage stage;
        TagInfo tagInfo;

        try (Engine engine = engine()) {
            stage = server.load(engine, stageName);
            tagInfo = stage.tagInfo(engine, stage.createRegistry(World.create() /* TODO */, engine));
        }
        server.checkFaultPermissions(User.authenticatedOrAnonymous().login, new ArrayList<>() /* TODO */);
        return tagInfo;
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
        try (Engine engine = engine()) {
            entries = server.load(engine, stage).accessLogAll(max);
            for (AccessLogEntry entry : entries) {
                result.add("[" + AccessLogEntry.DATE_FMT.format(entry.dateTime) + " " + entry.user + "] " + entry.clientCommand);
                if (details) {
                    for (DetailsLogEntry detail : server.detailsLog(entry.clientInvocation)) {
                        result.add(new JsonPrimitive("  " + detail.level + " " + detail.message));
                    }
                }
            }
        }
        return result.toString();
    }

    @GetMapping("/stages/{name}/logs")
    public String logs(@PathVariable(value = "name") String stageName) throws Exception {
        JsonArray result;
        FileNode dir;
        Stage stage;

        try (Engine engine = engine()) {
            stage = server.load(engine, stageName);
        }
        dir = stage.getLogs(); // TODO: application logs
        result = new JsonArray();
        for (FileNode file : dir.find("**/*")) {
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
        try (Engine engine = engine()) {
            stage = server.load(engine, stageName);
        }
        resource = new FileSystemResource(stage.getLogs().join(file).toPath());
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
