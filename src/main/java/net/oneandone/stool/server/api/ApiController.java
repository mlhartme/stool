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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.cli.Caller;
import net.oneandone.stool.core.Configuration;
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
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileAlreadyExistsException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Counter-part for Remote Client. */
@RestController
@RequestMapping("/api")
public class ApiController {
    private final ObjectMapper json; // TODO: threading
    private final Configuration configuration;
    private final UserManager userManager;

    @Autowired
    public ApiController(Configuration configuration, UserManager userManager) {
        this.json = new ObjectMapper();
        this.configuration = configuration;
        this.userManager = userManager;
    }

    //-- Client methods

    @GetMapping("/version")
    public String version(HttpServletRequest request) throws IOException {
        return client(request).version();
    }

    @GetMapping("/stages")
    public String list(@RequestParam(value = "filter", required = false, defaultValue = "") String filter,
                       @RequestParam(value = "select", required = false, defaultValue = "") String selectStr,
                       HttpServletRequest request) throws IOException {
        Map<String, Map<String, JsonNode>> map;
        ObjectNode obj;
        ObjectNode result;

        map = client(request).list(filter, "*".equals(selectStr) ? Collections.emptyList() : Separator.COMMA.split(selectStr));
        result = json.createObjectNode();
        for (Map.Entry<String, Map<String, JsonNode>> stage : map.entrySet()) {
            obj = json.createObjectNode();
            for (Map.Entry<String, JsonNode> o : stage.getValue().entrySet()) {
                obj.set(o.getKey(), o.getValue());
            }
            result.set(stage.getKey(), obj);
        }
        return result.toString();
    }

    @PostMapping("/stages/{stage}")
    public String create(@PathVariable("stage") String name, @RequestParam(value = "image", required = true) String image,
                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> values;

        values = map(request, "value.");
        try {
            return Json.obj(json, client(request).create(name, image, values)).toString();
        } catch (FileAlreadyExistsException e) {
            // OK, fall through
            response.sendError(409 /* conflict */, "stage exists: " + name);
            return "";
        }
    }

    private LocalClient client(HttpServletRequest request) {
        return new LocalClient("server", null, configuration,
                new Caller(
                        request.getHeader("X-stool-client-invocation"),
                        User.authenticatedOrAnonymous().login,
                        request.getHeader("X-stool-client-command"),
                        null /* TODO */));
    }

    @PostMapping("/stages/{stage}/publish")
    public String publish(@PathVariable(value = "stage") String stageName,
                          @RequestParam(value = "image", required = false) String explicitImage, HttpServletRequest request) throws IOException {
        String result;

        result = client(request).publish(stageName, explicitImage, map(request, "value."));
        return new TextNode(result).toString();
    }

    @GetMapping("/stages//{stage}/await-available")
    public String awaitAvailable(@PathVariable(value = "stage") String stage, HttpServletRequest request) throws IOException {
        return Json.obj(json, client(request).awaitAvailable(stage)).toString();
    }

    @PostMapping("/stages/{stage}/delete")
    public void delete(@PathVariable(value = "stage") String stage, HttpServletRequest request) throws IOException {
        client(request).delete(stage);
    }

    @GetMapping("/stages/{stage}/values")
    public String values(@PathVariable(value = "stage") String stage, HttpServletRequest request) throws IOException {
        return Json.obj(json, client(request).getValues(stage)).toString();
    }

    @PostMapping("/stages/{stage}/set-values")
    public String setValues(@PathVariable(value = "stage") String stage, HttpServletRequest request) throws IOException {
        return Json.obj(json, client(request).setValues(stage, map(request, ""))).toString();
    }

    @GetMapping("/stages/{stage}/history")
    public String history(@PathVariable(value = "stage") String stage, HttpServletRequest request) throws IOException {
        return array(json, client(request).history(stage)).toString();
    }

    @PostMapping("/stages/{stage}/validate")
    public String validate(@PathVariable(value = "stage") String stage, @RequestParam("email") boolean email, @RequestParam("repair") boolean repair,
                           HttpServletRequest request) throws IOException {
        return array(json, client(request).validate(stage, email, repair)).toString();
    }

    @GetMapping("/stages/{stage}/pod-token")
    public String podToken(@PathVariable(value = "stage") String stageName, @RequestParam("timeout") int timeout, HttpServletRequest request) throws IOException {
        return client(request).podToken(stageName, timeout).toObject(json).toString();
    }

    @GetMapping("/stages/{stage}/images")
    public String images(@PathVariable("stage") String name, HttpServletRequest request) throws Exception {
        return array(json, client(request).images(name)).toString();
    }

    //--

    @PostMapping("/auth")
    public String auth() throws IOException {
        User user;
        String result;

        if (configuration.ldapUrl.isEmpty()) {
            throw new IOException("authentication is disabled");
        }
        user = User.authenticatedOpt();
        if (user == null) {
            throw new IllegalStateException();
        }
        result = userManager.generateToken(user);
        return new TextNode(result).toString();
    }

    @GetMapping("/stages/{name}/logs")
    public String logs(@PathVariable(value = "name") String stageName, HttpServletRequest request) throws Exception {
        ArrayNode result;
        FileNode dir;
        Stage stage;

        try (Engine engine = client(request).engine()) {
            stage = configuration.load(engine, stageName);
        }
        dir = stage.getLogs(); // TODO: application logs
        result = json.createArrayNode();
        for (FileNode file : dir.find("**/*")) {
            if (!file.isDirectory()) {
                result.add(new TextNode(file.getRelative(dir)));
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
        try (Engine engine = client(request).engine()) {
            stage = configuration.load(engine, stageName);
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
