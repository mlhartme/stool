package net.oneandone.stool.server.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.common.Reference;
import net.oneandone.stool.server.docker.BuildError;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.util.AppInfo;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.LogEntry;
import net.oneandone.stool.server.util.LogReader;
import net.oneandone.stool.server.util.PredicateParser;
import net.oneandone.stool.server.util.Property;
import net.oneandone.stool.server.util.Session;
import net.oneandone.stool.server.util.Validation;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
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
    private final Session session;

    public ApiController() throws IOException {
        World world;
        Globals globals;

        world = World.create();
        globals = Globals.create(world, new String[0]);
        this.session = globals.session();
    }

    @GetMapping("/version")
    public String version() {
        return new JsonPrimitive( Main.versionString(session.world)).toString();
    }

    @GetMapping("/search")
    public String search(@RequestParam(value = "stageClause", required = false) String stageClause) throws IOException {
        JsonArray result;
        Map<String, IOException> problems;

        result = new JsonArray();
        problems = new HashMap<>();
        for (Stage stage : session.list(PredicateParser.parse(stageClause), problems)) {
            result.add(new JsonPrimitive(stage.getName()));
        }
        if (!problems.isEmpty()) {
            throw new IOException("nested problems: " + problems);
        }
        return result.toString();
    }

    @PostMapping("create")
    public void create(HttpServletRequest request) throws IOException {
        String name;
        Map<String, String> config;
        Stage stage;
        Property property;

        config = map(request, "");
        name = config.remove("name");
        if (name == null) {
            throw new IOException("missing stage name: " + name);
        }
        stage = session.create(name);
        for (Map.Entry<String, String> entry : config.entrySet()) {
            property = stage.propertyOpt(entry.getKey());
            if (property == null) {
                throw new ArgumentException("unknown property: " + entry.getKey());
            }
            property.set(entry.getValue());
        }
        stage.saveConfig();

        openStage(stage.getName());
        closeStage();
    }

    @PostMapping("/stage/{stage}/build")
    public String build(@PathVariable("stage") String stage, @RequestParam("app") String app,
                        @RequestParam("comment") String comment,
                        @RequestParam("origin") String origin, @RequestParam("created-by") String createdBy,
                        @RequestParam("created-on") String createdOn, @RequestParam("no-cache") boolean noCache,
                        @RequestParam("keep") int keep, InputStream body, HttpServletRequest request) throws Exception {
        String output;
        Map<String, String> arguments;
        FileNode war;

        arguments = map(request, "arg.");
        openStage(stage);

        war = session.world.getTemp().createTempFile();
        war.copyFileFrom(body);
        try {
            output = session.load(stage).build(app, war,
                    comment, origin, createdBy, createdOn, noCache, keep, arguments);
            return buildResult(null, output).toString();
        } catch (BuildError e) {
            return buildResult(e.error, e.output).toString();
        } finally {
            war.deleteFile();
            closeStage();
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

    @GetMapping("/stage/{stage}/properties")
    public String properties(@PathVariable(value = "stage") String stage) throws IOException {
        JsonObject result;

        result = new JsonObject();
        for (Property property : session.load(stage).properties()) {
            result.add(property.name(), new JsonPrimitive(property.get()));
        }
        return result.toString();
    }

    @PostMapping("/stage/{stage}/set-properties")
    public String setProperties(@PathVariable(value = "stage") String stageName, HttpServletRequest request) throws IOException {
        Stage stage;
        Property prop;
        String value;
        Map<String, String> arguments;
        JsonObject result;

        stage = session.load(stageName);
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


    @GetMapping("stage/{stage}/status")
    public String status(@PathVariable(value = "stage") String stage, @RequestParam("select") String select) throws IOException {
        JsonObject result;
        List<String> selection;

        if (select == null || select.isEmpty()) {
            selection = null;
        } else {
            selection = Separator.COMMA.split(select);
        }
        result = new JsonObject();
        for (Info info : session.load(stage).fields()) {
            if (selection == null || selection.remove(info.name())) {
                result.add(info.name(), new JsonPrimitive(info.getAsString()));
            }
        }
        if (selection != null && !selection.isEmpty()) {
            throw new IOException("unknown field(s): " + selection);
        }
        return result.toString();
    }

    @GetMapping("stage/{stage}/apps")
    public String apps(@PathVariable(value = "stage") String stage) throws IOException {
        List<String> result;

        result = new ArrayList<>(session.load(stage).images(session.dockerEngine()).keySet());
        Collections.sort(result);
        return array(result).toString();
    }

    @PostMapping("validate")
    public String validate(@RequestParam("stageClause") String stageClause,
                           @RequestParam("email") boolean email, @RequestParam("repair") boolean repair) throws IOException {
        List<String> output;

        try {
            output = new Validation(session).run(stageClause, email, repair);
        } catch (MessagingException e) {
            throw new IOException("email failure: " + e.getMessage(), e);
        } catch (NamingException e) {
            throw new IOException("naming exception: " + e.getMessage(), e);
        }
        return array(output).toString();
    }

    @GetMapping("stage/{stage}/appInfo")
    public String appInfo(@PathVariable("stage") String stage, @RequestParam("app") String app) throws Exception {
        return array(new AppInfo(session).run(stage, app)).toString();
    }


    @PostMapping("stage/{stage}/start")
    public void start(@PathVariable(value = "stage") String stageName,
                      @RequestParam("http") int http, @RequestParam("https") int https,
                      HttpServletRequest request) throws IOException {
        Stage stage;
        int global;
        int reserved;
        Map<String, String> environment;
        Map<String, Integer> apps;

        apps = intMap(map(request, "app."));
        environment = new HashMap<>(session.configuration.environment);
        environment.putAll(map(request, "env."));
        openStage(stageName);
        try {
            global = session.configuration.quota;
            if (global != 0) {
                reserved = session.quotaReserved();
                if (reserved > global) {
                    throw new IOException("Sum of all stage quotas exceeds global limit: " + reserved + " mb > " + global + " mb.\n"
                            + "Use 'stool list name disk quota' to see actual disk usage vs configured quota.");
                }
            }

            stage = session.load(stageName);
            stage.session.configuration.verfiyHostname();
            stage.checkConstraints();
            stage.start(http, https, environment, apps);
        } finally {
            closeStage();
        }
    }

    @GetMapping("stage/{stage}/await-startup")
    public String awaitStartup(@PathVariable(value = "stage") String stageName) throws IOException {
        Stage stage;
        JsonObject result;

        stage = session.load(stageName);
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

    @PostMapping("stage/{stage}/stop")
    public void stop(@PathVariable(value = "stage") String stage, @RequestParam("apps") String apps) throws IOException {
        openStage(stage);
        try {
            session.load(stage).stop(Separator.COMMA.split(apps));
        } finally {
            closeStage();
        }
    }


    @GetMapping("stage/{stage}/history")
    public String history(@PathVariable(value = "stage") String stage,
                          @RequestParam("details") boolean details, @RequestParam("max") int max) throws IOException {
        LogEntry entry;
        Map<String, List<LogEntry>> detailsMap; /* maps id to it's details */
        LogReader reader;
        List<LogEntry> lst;
        int counter;
        JsonArray result;

        result = new JsonArray();
        counter = 0;
        detailsMap = new HashMap<>();
        reader = session.load(stage).logReader();
        while (true) {
            entry = reader.prev();
            if (entry == null) {
                break;
            }
            lst = detailsMap.get(entry.requestId);
            if (lst == null) {
                lst = new ArrayList<>();
                detailsMap.put(entry.requestId, lst);
            }
            if (entry.logger.equals("COMMAND")) {
                detailsMap.remove(entry.requestId);
                lst.add(entry);
                if (forStage(stage, lst)) {
                    counter++;
                    result.add("[" + LogEntry.FULL_FMT.format(entry.dateTime) + " " + entry.user + "] " + entry.message);
                    if (details) {
                        for (int i = lst.size() - 1; i >= 0; i--) {
                            result.add(Strings.indent(lst.get(i).message, "     "));
                        }
                    }
                }
                if (counter == max) {
                    result.add("(skipping after " + max + " commands; use -max <n> to see more)");
                    break;
                }
            } else {
                lst.add(entry);
            }
        }
        return result.toString();
    }

    private static boolean forStage(String stageName, List<LogEntry> lst) {
        for (LogEntry entry : lst) {
            if (stageName.equals(entry.stageName)) {
                return true;
            }
        }
        return false;
    }

    @PostMapping("stage/{stage}/remove")
    public void remove(@PathVariable(value = "stage") String stage) throws IOException {
        openStage(stage);
        try {
            session.load(stage).remove();
        } finally {
            closeStage();
        }
    }

    //--

    @GetMapping("/quota")
    public String quota() throws IOException {
        int global;

        global = session.configuration.quota;
        return new JsonPrimitive(global == 0 ? "" : session.quotaReserved() + "/" + global).toString();
    }

    @GetMapping("/memUnreserved")
    public String memUnreserved() throws IOException {
        return new JsonPrimitive(session.memUnreserved()).toString();
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

    //--
    private void openStage(String stage) throws MkdirException {
        session.logging.openStage(stage);
        session.logging.command(session.command);
    }

    private void closeStage() {
        session.logging.closeStage();
    }

}
