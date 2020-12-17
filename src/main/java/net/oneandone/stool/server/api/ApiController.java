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
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.Stage;
import net.oneandone.stool.server.users.User;
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final Server server;
    private final LocalClient client;

    @Autowired
    public ApiController(Server server) {
        this.server = server;
        this.client = new LocalClient(server);
    }

    // TODO: used for readiness probes - becomes expensive if I used quotas ...
    @GetMapping("/version")
    public String info() throws IOException {
        return client.version();
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
        Map<String, Map<String, JsonElement>> map;
        JsonObject obj;
        JsonObject result;

        map = client.list(filter, "*".equals(selectStr) ? null : Separator.COMMA.split(selectStr));
        result = new JsonObject();
        for (Map.Entry<String, Map<String, JsonElement>> entry : map.entrySet()) {
            obj = new JsonObject();
            for (Map.Entry<String, JsonElement> o : entry.getValue().entrySet()) {
                result.add(o.getKey(), o.getValue());
            }
            result.add(entry.getKey(), obj);
        }
        return result.toString();
    }

    @PostMapping("/stages/{stage}")
    public String create(@PathVariable("stage") String name, @RequestParam(value = "image", required = true) String image,
                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> values;

        values = map(request, "value.");
        try {
            return Engine.obj(client.create(name, image, values)).toString();
        } catch (FileAlreadyExistsException e) {
            // OK, fall through
            response.sendError(409 /* conflict */, "stage exists: " + name);
            return "";
        }
    }

    @PostMapping("/stages/{stage}/publish")
    public String publish(@PathVariable(value = "stage") String stageName,
                          @RequestParam(value = "image", required = false) String explicitImage, HttpServletRequest request) throws IOException {
        String result;

        result = client.publish(stageName, explicitImage, map(request, "value."));
        return new JsonPrimitive(result).toString();
    }

    @PostMapping("/stages/{stage}/delete")
    public void delete(@PathVariable(value = "stage") String stage) throws IOException {
        client.delete(stage);
    }

    @GetMapping("/stages/{stage}/values")
    public String values(@PathVariable(value = "stage") String stage) throws IOException {
        return Engine.obj(client.getValues(stage)).toString();
    }

    @PostMapping("/stages/{stage}/set-values")
    public String setValues(@PathVariable(value = "stage") String stage, HttpServletRequest request) throws IOException {
        return Engine.obj(client.setValues(stage, map(request, ""))).toString();
    }

    @PostMapping("/stages/{stage}/validate")
    public String validate(@PathVariable(value = "stage") String stage, @RequestParam("email") boolean email, @RequestParam("repair") boolean repair) throws IOException {
        return array(client.validate(stage, email, repair)).toString();
    }

    @GetMapping("/stages/{stage}/images")
    public String images(@PathVariable("stage") String name) throws Exception {
        return array(client.images(name)).toString();
    }


    @GetMapping("/stages//{stage}/await-available")
    public String awaitAvailable(@PathVariable(value = "stage") String stage) throws IOException {
        return Engine.obj(client.awaitAvailable(stage)).toString();
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
        return client.podToken(stageName, timeout).toJson().toString();
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
        return array(client.history(stage, details, max)).toString();
    }

    @GetMapping("/stages/{name}/logs")
    public String logs(@PathVariable(value = "name") String stageName) throws Exception {
        JsonArray result;
        FileNode dir;
        Stage stage;

        try (Engine engine = client.engine()) {
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
        try (Engine engine = client.engine()) {
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
