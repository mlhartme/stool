package net.oneandone.stool.cli;

import net.oneandone.inline.Console;
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.util.Project;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

public class ClientSession {
    private final Session session;

    public ClientSession(Session session) {
        this.session = session;
    }


    public void build(Reference reference, Project project, String app, FileNode war, Console console, String comment,
                      String origin, String createdBy, String createdOn, boolean noCache, int keep) throws Exception {
        session.load(reference).build(project, app, war, console, comment, origin, createdBy, createdOn, noCache, keep);
    }
}
