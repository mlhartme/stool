package net.oneandone.stool.server.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.stool.common.Reference;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.LogEntry;
import net.oneandone.stool.server.util.LogReader;
import net.oneandone.stool.server.util.PredicateParser;
import net.oneandone.stool.server.util.Property;
import net.oneandone.stool.server.util.Server;
import net.oneandone.stool.server.util.Session;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class RestController {
    private final Session session;

    public RestController() throws IOException {
        World world;
        Globals globals;

        world = World.create();
        globals = Globals.create(world, world.file("/Users/mhm/Projects/github.com/net/oneandone/stool/stool/client/target/it/git"), // TODO
                new String[0]);
        this.session = globals.session();
        System.out.println("session created: " + session);
    }

    @GetMapping("/version") @ResponseBody
    public String version() throws IOException {
        return new JsonPrimitive( Server.versionString(World.create())).toString();
    }

    @GetMapping("/search") @ResponseBody
    public String search(@RequestParam("stageClause") String stageClause) throws IOException {
        JsonArray result;
        Map<String, IOException> problems;

        result = new JsonArray();
        problems = new HashMap<>();
        for (Stage stage : session.list(PredicateParser.parse(stageClause), problems)) {
            result.add(new JsonPrimitive(stage.reference.getName()));
        }
        if (!problems.isEmpty()) {
            throw new IOException("nested problems: " + problems);
        }
        return result.toString();
    }

    @GetMapping("/stage/{stage}/properties") @ResponseBody
    public String properties(@PathVariable(value = "stage") String stage) throws IOException {
        JsonObject result;

        result = new JsonObject();
        for (Property property : session.load(new Reference(stage)).properties()) {
            result.add(property.name(), new JsonPrimitive(property.get()));
        }
        return result.toString();
    }

    @GetMapping("stage/{stage}/status") @ResponseBody
    public String status(@PathVariable(value = "stage") String stage, @RequestParam("select") String select) throws IOException {
        JsonObject result;
        List<String> selection;

        if (select == null || select.isEmpty()) {
            selection = null;
        } else {
            selection = Separator.COMMA.split(select);
        }
        result = new JsonObject();
        for (Info info : session.load(new Reference(stage)).fields()) {
            if (selection == null || selection.remove(info.name())) {
                result.add(info.name(), new JsonPrimitive(info.getAsString()));
            }
        }
        if (selection != null && !selection.isEmpty()) {
            throw new IOException("unknown field(s): " + selection);
        }
        return result.toString();
    }

    @PostMapping("stage/{stage}/stop") @ResponseBody
    public void stop(@PathVariable(value = "stage") String stage, @RequestParam("apps") String apps) throws IOException {
        Reference reference;

        reference = new Reference(stage);
        openStage(reference);
        try {
            session.load(reference).stop(Separator.COMMA.split(apps));
        } finally {
            closeStage();
        }
    }


    @GetMapping("stage/{stage}/history") @ResponseBody
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
        reader = session.load(new Reference(stage)).logReader();
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

    @PostMapping("stage/{stage}/remove") @ResponseBody
    public void remove(@PathVariable(value = "stage") String stage) throws IOException {
        Reference reference;

        reference = new Reference(stage);
        openStage(reference);
        try {
            session.load(reference).remove();
        } finally {
            closeStage();
        }
    }

    //--

    @GetMapping("/quota") @ResponseBody
    public String quota() throws IOException {
        int global;

        global = session.configuration.quota;
        return new JsonPrimitive(global == 0 ? "" : session.quotaReserved() + "/" + global).toString();
    }

    @GetMapping("/memUnreserved") @ResponseBody
    public String memUnreserved() throws IOException {
        return new JsonPrimitive(session.memUnreserved()).toString();
    }


    //--
    private void openStage(Reference reference) throws MkdirException {
        session.logging.openStage(reference.getName());
        session.logging.command(session.command);
    }

    private void closeStage() {
        session.logging.closeStage();
    }

}
