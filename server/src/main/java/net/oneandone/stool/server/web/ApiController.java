package net.oneandone.stool.server.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.server.Main;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.docker.BuildError;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.logging.DetailsLogEntry;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.users.User;
import net.oneandone.stool.server.util.AppInfo;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.PredicateParser;
import net.oneandone.stool.server.util.Property;
import net.oneandone.stool.server.util.Validation;
import net.oneandone.sushi.archive.ArchiveException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.mail.MessagingException;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final Server server;

    @Autowired
    public ApiController(Server server) {
        this.server = server;
    }

    @GetMapping("/version")
    public String version() {
        return new JsonPrimitive( Main.versionString(server.world)).toString();
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
    public String list(@RequestParam(value = "filter", required = false, defaultValue = "") String filter) throws IOException {
        JsonArray result;
        Map<String, IOException> problems;

        result = new JsonArray();
        problems = new HashMap<>();
        for (Stage stage : server.list(PredicateParser.parse(filter), problems)) {
            result.add(new JsonPrimitive(stage.getName()));
        }
        if (!problems.isEmpty()) {
            throw new IOException("nested problems: " + problems);
        }
        return result.toString();
    }

    @PostMapping("/stages/{stage}")
    public void create(@PathVariable("stage") String name, HttpServletRequest request) throws IOException {
        Map<String, String> config;
        Stage stage;
        Property property;

        config = map(request, "");
        stage = server.create(name);
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
    public String build(@PathVariable("stage") String stage, @RequestParam("app") String app,
                        @RequestParam("comment") String comment,
                        @RequestParam("origin") String origin, @RequestParam("created-by") String createdBy,
                        @RequestParam("created-on") String createdOn, @RequestParam("no-cache") boolean noCache,
                        @RequestParam("keep") int keep, InputStream body, HttpServletRequest request) throws Exception {
        String output;
        Map<String, String> arguments;
        FileNode war;

        arguments = map(request, "arg.");

        war = server.world.getTemp().createTempFile();
        war.copyFileFrom(body);
        try {
            output = server.load(stage).build(app, war, comment, origin, createdBy, createdOn, noCache, keep, arguments);
            return buildResult(null, output).toString();
        } catch (BuildError e) {
            return buildResult(e.error, e.output).toString();
        } finally {
            war.deleteFile();
        }
    }

    private JsonObject buildResult(String error, String output) {
        JsonObject result;

        result = new JsonObject();
        if (error != null) {
            result.add("error", new JsonPrimitive(error));
        }
        result.add("output", new JsonPrimitive(output));
        return result;
    }

    @GetMapping("/stages/{stage}/properties")
    public String properties(@PathVariable(value = "stage") String stage) throws IOException {
        JsonObject result;

        result = new JsonObject();
        for (Property property : server.load(stage).properties()) {
            result.add(property.name(), new JsonPrimitive(property.get()));
        }
        return result.toString();
    }

    @PostMapping("/stages/{stage}/set-properties")
    public String setProperties(@PathVariable(value = "stage") String stageName, HttpServletRequest request) throws IOException {
        Stage stage;
        Property prop;
        String value;
        Map<String, String> arguments;
        JsonObject result;

        stage = server.load(stageName);
        arguments = map(request, "");
        result = new JsonObject();
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            prop = stage.propertyOpt(entry.getKey());
            if (prop == null) {
                throw new ArgumentException("unknown property: " + entry.getKey());
            }
            value = entry.getValue();
            value = value.replace("{}", prop.get());
            try {
                prop.set(value);
                result.add(prop.name(), new JsonPrimitive(prop.getAsString()));
            } catch (RuntimeException e) {
                throw new ArgumentException("invalid value for property " + prop.name() + " : " + e.getMessage());
            }
        }
        stage.saveConfig();
        return result.toString();
    }


    @GetMapping("/stages/{stage}/status")
    public String status(@PathVariable(value = "stage") String stage, @RequestParam(value = "select", required = false) String select) throws IOException {
        JsonObject result;
        List<String> selection;

        if (select == null || select.isEmpty()) {
            selection = null;
        } else {
            selection = Separator.COMMA.split(select);
        }
        result = new JsonObject();
        for (Info info : server.load(stage).fields()) {
            if (selection == null || selection.remove(info.name())) {
                result.add(info.name(), new JsonPrimitive(info.getAsString()));
            }
        }
        if (selection != null && !selection.isEmpty()) {
            throw new IOException("unknown field(s): " + selection);
        }
        return result.toString();
    }

    @GetMapping("/stages/{stage}/apps")
    public String apps(@PathVariable(value = "stage") String stage) throws IOException {
        List<String> result;

        result = new ArrayList<>(server.load(stage).images(server.dockerEngine()).keySet());
        Collections.sort(result);
        return array(result).toString();
    }

    @PostMapping("/stages/{stage}/validate")
    public String validate(@PathVariable(value = "stage") String stage, @RequestParam("email") boolean email, @RequestParam("repair") boolean repair) throws IOException {
        List<String> output;

        try {
            output = new Validation(server).run(stage, email, repair);
        } catch (MessagingException e) {
            throw new IOException("email failure: " + e.getMessage(), e);
        } catch (NamingException e) {
            throw new IOException("naming exception: " + e.getMessage(), e);
        }
        return array(output).toString();
    }

    @GetMapping("/stages/{stage}/appInfo")
    public String appInfo(@PathVariable("stage") String stage, @RequestParam("app") String app) throws Exception {
        return array(new AppInfo(server).run(stage, app)).toString();
    }


    @PostMapping("/stages/{stage}/start")
    public void start(@PathVariable(value = "stage") String stageName,
                      @RequestParam("http") int http, @RequestParam("https") int https,
                      HttpServletRequest request) throws IOException {
        Stage stage;
        int global;
        int reserved;
        Map<String, String> environment;
        Map<String, Integer> apps;

        apps = intMap(map(request, "app."));
        environment = new HashMap<>(server.configuration.environment);
        environment.putAll(map(request, "env."));
        global = server.configuration.quota;
        if (global != 0) {
            reserved = server.quotaReserved();
            if (reserved > global) {
                throw new IOException("Sum of all stage quotas exceeds global limit: " + reserved + " mb > " + global + " mb.\n"
                        + "Use 'stool list name disk quota' to see actual disk usage vs configured quota.");
            }
        }

        stage = server.load(stageName);
        stage.server.configuration.verfiyHostname();
        stage.checkConstraints();
        stage.start(http, https, environment, apps);
    }

    @GetMapping("/stages//{stage}/await-startup")
    public String awaitStartup(@PathVariable(value = "stage") String stageName) throws IOException {
        Stage stage;
        JsonObject result;

        stage = server.load(stageName);
        stage.awaitStartup();

        result = new JsonObject();
        for (String app : stage.currentMap().keySet()) {
            result.add(app, array(stage.namedUrls(app)));
        }
        return result.toString();
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
    public ResponseEntity<?> stop(@PathVariable(value = "stage") String stage, @RequestParam("apps") String apps) throws IOException {
        List<String> result;

        try {
            result = server.load(stage).stop(Separator.COMMA.split(apps));
        } catch (ArgumentException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(array(result).toString(), HttpStatus.OK);
    }


    @GetMapping("/stages/{stage}/history")
    public String history(@PathVariable(value = "stage") String stage,
                          @RequestParam("details") boolean details, @RequestParam("max") int max) throws IOException {
        List<AccessLogEntry> entries;
        JsonArray result;

        result = new JsonArray();
        entries = server.load(stage).accessLog(max);
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
        server.load(stage).remove();
    }

    //--

    @GetMapping("/quota")
    public String quota() throws IOException {
        int global;

        global = server.configuration.quota;
        return new JsonPrimitive(global == 0 ? "" : server.quotaReserved() + "/" + global).toString();
    }

    @GetMapping("/memUnreserved")
    public String memUnreserved() throws IOException {
        return new JsonPrimitive(server.memUnreserved()).toString();
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
                result.put(parameter, request.getParameter(parameter));
            }
        }
        return result;
    }

    private Map<String, Integer> intMap(Map<String, String> map) {
        Map<String, Integer> result;

        result = new HashMap<>(map.size());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            result.put(entry.getKey(), Integer.parseInt(entry.getValue()));
        }
        return result;
    }
}
