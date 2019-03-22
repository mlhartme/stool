package net.oneandone.stool.util;

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.stage.State;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Server {
    private final Session session;

    public Server(Session session) {
        this.session = session;
    }

    public String getName(Reference reference) throws IOException {
        return session.load(reference).getName();
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
