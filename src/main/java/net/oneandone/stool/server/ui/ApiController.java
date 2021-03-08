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
package net.oneandone.stool.server.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.cli.Caller;
import net.oneandone.stool.cli.KubernetesClient;
import net.oneandone.stool.core.LocalSettings;
import net.oneandone.stool.core.StageNotFoundException;
import net.oneandone.stool.directions.DirectionsRef;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.core.Stage;
import net.oneandone.stool.server.users.User;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.util.Json;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Counter-part for Remote Client. */
@RestController
@RequestMapping("/api")
public class ApiController {
    private static final int POOL_SIZE = 1;

    private final LocalSettings globalSettings;
    private final UserManager globalUserManager;
    private final List<RequestSettings> pool;

    @Autowired
    public ApiController(LocalSettings localSettings, UserManager userManager) {
        this.globalSettings = localSettings;
        this.globalUserManager = userManager;
        this.pool = new ArrayList<>();  // starts empty, filled when giving back session
    }

    public RequestSettings openSettings() throws IOException {
        synchronized (pool) {
            if (pool.isEmpty()) {
                return new RequestSettings(globalSettings);
            } else {
                return pool.remove(0);
            }
        }
    }

    public void closeSettings(RequestSettings settings) {
        synchronized (pool) {
            if (pool.size() < POOL_SIZE) {
                pool.add(settings);
            }
        }
    }

    private class RequestSettings extends LocalSettings implements Closeable  {
        RequestSettings(LocalSettings from) throws IOException {
            super(from);
        }

        private KubernetesClient client(HttpServletRequest request) {
            return new KubernetesClient(this, "server", null,
                    new Caller(
                            request.getHeader("X-stool-client-invocation"),
                            User.authenticatedOrAnonymous().login,
                            request.getHeader("X-stool-client-command"),
                            null /* TODO */));
        }

        @Override
        public void close() {
            closeSettings(this);
        }
    }

    //-- Client methods

    @GetMapping("/version")
    public String version(HttpServletRequest request) throws IOException {
        try (RequestSettings session = openSettings()) {
            return session.client(request).version();
        }
    }

    @GetMapping("/stages")
    public String list(@RequestParam(value = "filter", required = false, defaultValue = "") String filter,
                       @RequestParam(value = "select", required = false, defaultValue = "") String selectStr,
                       @RequestParam(value = "hidden", required = false) boolean hidden,
                       HttpServletRequest request) throws IOException {
        Map<String, Map<String, JsonNode>> map;
        ObjectNode obj;
        ObjectNode result;

        try (RequestSettings settings = openSettings()) {
            map = settings.client(request).list(filter, "*".equals(selectStr) ? Collections.emptyList() : Separator.COMMA.split(selectStr), hidden);
            result = settings.json.createObjectNode();
            for (Map.Entry<String, Map<String, JsonNode>> stage : map.entrySet()) {
                obj = settings.json.createObjectNode();
                for (Map.Entry<String, JsonNode> o : stage.getValue().entrySet()) {
                    obj.set(o.getKey(), o.getValue());
                }
                result.set(stage.getKey(), obj);
            }
        }
        return result.toString();
    }

    @PostMapping("/stages/{stage}")
    public String create(@PathVariable("stage") String name, @RequestParam(value = "directionsref") String directionsRef,
                         HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> values;

        values = map(request, "value.");
        try (RequestSettings settings = openSettings()) {
            return Json.obj(settings.json, settings.client(request).create(name, DirectionsRef.parse(directionsRef), values)).toString();
        } catch (FileAlreadyExistsException e) {
            // OK, fall through
            response.sendError(409 /* conflict */, "stage exists: " + name);
            return "";
        }
    }

    @PostMapping("/stages/{stage}/publish")
    public String publish(@PathVariable(value = "stage") String stageName, @RequestParam(value = "directionsref", required = false) String directionsRefOpt,
                          @RequestParam(value = "dryrun", required = false, defaultValue = "false") boolean dryrun,
                          @RequestParam(value = "allow", required = false) String allow, HttpServletRequest request) throws IOException {
        try (RequestSettings settings = openSettings()) {
            return array(settings.json, settings.client(request)
                    .publish(stageName, dryrun, allow, directionsRefOpt == null ? null : DirectionsRef.parse(directionsRefOpt), map(request, "value.")).toList()).toString();
        }
    }

    @GetMapping("/stages//{stage}/await-available")
    public String awaitAvailable(@PathVariable(value = "stage") String stage, HttpServletRequest request) throws IOException {
        try (RequestSettings settings = openSettings()) {
            return Json.obj(settings.json, settings.client(request).awaitAvailable(stage)).toString();
        }
    }

    @PostMapping("/stages/{stage}/delete")
    public void delete(@PathVariable(value = "stage") String stage, HttpServletRequest request) throws IOException {
        try (RequestSettings settings = openSettings()) {
            settings.client(request).delete(stage);
        }
    }

    @GetMapping("/stages/{stage}/values")
    public String values(@PathVariable(value = "stage") String stage, HttpServletRequest request) throws IOException {
        try (RequestSettings settings = openSettings()) {
            return Json.objPairs(settings.json, settings.client(request).getValues(stage)).toString();
        }
    }

    @PostMapping("/stages/{stage}/set-values")
    public String setValues(@PathVariable(value = "stage") String stage, HttpServletRequest request) throws IOException {
        try (RequestSettings settings = openSettings()) {
            return Json.obj(settings.json, settings.client(request).setValues(stage, map(request, ""))).toString();
        }
    }

    @GetMapping("/stages/{stage}/history")
    public String history(@PathVariable(value = "stage") String stage, HttpServletRequest request) throws IOException {
        try (RequestSettings settings = openSettings()) {
            return array(settings.json, settings.client(request).history(stage)).toString();
        }
    }

    @PostMapping("/stages/{stage}/validate")
    public String validate(@PathVariable(value = "stage") String stage, @RequestParam("email") boolean email, @RequestParam("repair") boolean repair,
                           HttpServletRequest request) throws IOException {
        try (RequestSettings settings = openSettings()) {
            return array(settings.json, settings.client(request).validate(stage, email, repair)).toString();
        }
    }

    @GetMapping("/stages/{stage}/pod-token")
    public String podToken(@PathVariable(value = "stage") String stageName, @RequestParam("timeout") int timeout, HttpServletRequest request) throws IOException {
        try (RequestSettings settings = openSettings()) {
            return settings.client(request).podToken(stageName, timeout).toObject(settings.json).toString();
        }
    }

    @GetMapping("/images/{image}")
    public String images(@PathVariable("image") String name, HttpServletRequest request) throws Exception {
        try (RequestSettings settings = openSettings()) {
            return array(settings.json, settings.client(request).images(name)).toString();
        }
    }

    //--

    @PostMapping("/auth")
    public String auth() throws IOException {
        User user;
        String result;

        try (RequestSettings settings = openSettings()) {
            if (!settings.auth()) {
                throw new IOException("authentication is disabled");
            }
            user = User.authenticatedOpt();
            if (user == null) {
                throw new IllegalStateException();
            }
            result = globalUserManager.generateToken(user);
            return new TextNode(result).toString();
        }
    }

    @GetMapping("/stages/{name}/logs")
    public String logs(@PathVariable(value = "name") String stageName, HttpServletRequest request) throws Exception {
        ArrayNode result;
        FileNode dir;
        Stage stage;

        try (RequestSettings settings = openSettings(); Engine engine = settings.client(request).engine()) {
            stage = settings.load(engine, stageName);
            dir = stage.getLogs(); // TODO: application logs
            result = settings.json.createArrayNode();
            for (FileNode file : dir.find("**/*")) {
                if (!file.isDirectory()) {
                    result.add(new TextNode(file.getRelative(dir)));
                }
            }
            return result.toString();
        }
    }

    @GetMapping(value = "/stages/{name}/logs/**", produces = "text/plain")
    public ResponseEntity<Resource> log(@PathVariable(value = "name") String stageName, HttpServletRequest request) throws Exception {
        Stage stage;
        Resource resource;
        String file;

        file = request.getRequestURI();
        file = Strings.removeLeft(file, request.getContextPath());
        file = Strings.removeLeft(file, "/api/stages/" + stageName + "/logs/");
        try (RequestSettings settings = openSettings(); Engine engine = settings.client(request).engine()) {
            stage = settings.load(engine, stageName);
        }
        resource = new FileSystemResource(stage.getLogs().join(file).toPath());
        return new ResponseEntity<>(resource, HttpStatus.OK);
    }

    //--

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

    @ExceptionHandler({ IOException.class })
    public void handleException(IOException e, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.METHOD_FAILURE.value());
        try (PrintWriter dest = response.getWriter()) {
            dest.println(e.getMessage());
        }
    }

    //--

    public static ArrayNode array(ObjectMapper mapper, List<String> array) {
        ArrayNode result;

        result = mapper.createArrayNode();
        for (String str : array) {
            result.add(new TextNode(str));
        }
        return result;
    }

    private Map<String, String> map(HttpServletRequest request, String prefix) {
        Map<String, String> result;
        Enumeration<String> parameters;
        String parameter;

        result = new LinkedHashMap<>();
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
