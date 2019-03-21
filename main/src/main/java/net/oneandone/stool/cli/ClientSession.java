package net.oneandone.stool.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Project;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.Map;

public class ClientSession {
    private final Session session;

    public ClientSession(Session session) {
        this.session = session;
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
