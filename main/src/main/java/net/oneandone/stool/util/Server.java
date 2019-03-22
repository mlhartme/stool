package net.oneandone.stool.util;

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.cli.EnumerationFailed;
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.stage.State;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
    public final Session session;

    public Server(Session session) {
        this.session = session;
    }

    public String getName(Reference reference) throws IOException {
        return session.load(reference).getName();
    }

    public List<Reference> list(EnumerationFailed problems, Predicate predicate) throws IOException {
        List<Reference> result;

        result = new ArrayList<>();
        for (Stage stage : session.list(problems, predicate)) {
            result.add(stage.reference);
        }
        return result;
    }

    public List<Info> status(Reference reference, List<String> selected) throws IOException {
        Stage stage;
        List<Info> result;

        stage = session.load(reference);
        if (selected.isEmpty()) {
            if (selected.isEmpty()) {
                for (Info info : stage.fieldsAndName()) {
                    selected.add(info.name());
                }
            }
        }
        result = new ArrayList<>();
        for (String name : selected) {
            result.add(stage.info(name));
        }
        return result;
    }

    public List<String> history(Reference reference, boolean details, int max) throws IOException {
        String stageId;
        LogEntry entry;
        Map<String, List<LogEntry>> detailsMap; /* id to it's details */
        LogReader reader;
        List<LogEntry> lst;
        int counter;
        List<String> result;

        result = new ArrayList<>();
        stageId = reference.getId();
        counter = 0;
        detailsMap = new HashMap<>();
        reader = session.load(reference).logReader();
        while (true) {
            entry = reader.prev();
            if (entry == null) {
                break;
            }
            lst = detailsMap.get(entry.id);
            if (lst == null) {
                lst = new ArrayList<>();
                detailsMap.put(entry.id, lst);
            }
            if (entry.logger.equals("COMMAND")) {
                detailsMap.remove(entry.id);
                if (forStage(stageId, lst)) {
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
        return result;
    }

    private static boolean forStage(String stageId, List<LogEntry> lst) {
        for (LogEntry entry : lst) {
            if (stageId.equals(entry.stageId)) {
                return true;
            }
        }
        return false;
    }

    public void rotateLogs(Reference reference) throws IOException {
        session.load(reference).rotateLogs(session.console);
    }

    public void remove(Reference reference) throws IOException {
        Stage stage;

        stage = session.load(reference);
        stage.wipeDocker(session.dockerEngine());
        stage.getDirectory().deleteTree();
    }

    public State state(Reference reference) throws IOException {
        return session.load(reference).state();
    }

    public void start(Reference reference, int http, int https, Map<String, String> environment, Map<String, Integer> apps) throws IOException {
        Stage stage;

        stage = session.load(reference);
        // to avoid running into a ping timeout below:
        stage.session.configuration.verfiyHostname();
        stage.checkConstraints();
        stage.start(session.console,  http, https, environment, apps);
    }

    public void stop(Reference reference, List<String> apps) throws IOException {
        session.load(reference).stop(session.console, apps);
    }

    public void awaitStartup(Reference reference) throws IOException, InterruptedException {
        Stage stage;

        stage = session.load(reference);
        stage.awaitStartup(session.console);
    }

    public Reference resolveName(String name) throws IOException {
        return session.loadByName(name).reference;
    }

    public void create(Project backstage, FileNode project, Map<String, String> config, Console console) throws IOException {
        Stage stage;
        Property property;

        stage = session.create(backstage.getOrigin());
        backstage.setAttached(stage.reference);
        stage.configuration.name = project.getName();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            property = stage.propertyOpt(entry.getKey());
            if (property == null) {
                throw new ArgumentException("unknown property: " + entry.getKey());
            }
            property.set(entry.getValue());
        }
        Project.checkName(stage.configuration.name);
        stage.saveConfig();

        session.logging.openStage(stage.reference.getId(), stage.getName());
        console.info.println("stage create: " + stage.getName());
        session.logging.closeStage();

    }
    public void build(Reference reference, Project project, String app, FileNode war, Console console, String comment,
                      String origin, String createdBy, String createdOn, boolean noCache, int keep) throws Exception {
        session.load(reference).build(project, app, war, console, comment, origin, createdBy, createdOn, noCache, keep);
    }
}
