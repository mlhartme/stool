package net.oneandone.stool.server.util;

import net.oneandone.stool.common.BuildResult;
import net.oneandone.stool.common.Reference;
import net.oneandone.stool.server.docker.BuildError;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.Map;

public class Server {
    private final Session session;

    public Server(Session session) {
        this.session = session;
    }

    //-- create, build, start, stop, remove

    public BuildResult build(Reference reference, String app, FileNode war, String comment,
                             String origin, String createdBy, String createdOn, boolean noCache, int keep,
                             Map<String, String> arguments) throws Exception {
        String output;

        openStage(reference);
        try {
            output = session.load(reference).build(app, war, comment, origin, createdBy, createdOn, noCache, keep, arguments);
            return new BuildResult(null, output);
        } catch (BuildError e) {
            return new BuildResult(e.error, e.output);
        } finally {
            closeStage();
        }
    }

    //-- app info

    private void openStage(Reference reference) throws MkdirException {
        session.logging.openStage(reference.getName());
        session.logging.command(session.command);
    }

    private void closeStage() {
        session.logging.closeStage();
    }

    //--

    public static String versionString(World world) {
        // don't use class.getPackage().getSpecificationVersion() because META-INF/META.MF
        // 1) is not available in Webapps (in particular: dashboard)
        // 2) is not available in test cases
        try {
            return world.resource("stool.version").readString().trim();
        } catch (IOException e) {
            throw new IllegalStateException("cannot determine version", e);
        }
    }
}
