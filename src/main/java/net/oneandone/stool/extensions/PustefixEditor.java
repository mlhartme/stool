package net.oneandone.stool.extensions;

import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.xml.XmlException;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PustefixEditor implements Extension {
    private static final String PREFIX = "cms.";

    private final String version;

    private final String userdata;

    public PustefixEditor() {
        this("0.18.75", "https://svn.1and1.org/svn/sales/workspaces/editor/userdata.xml");
    }

    public PustefixEditor(String version, String userdata) {
        this.version = version;
        this.userdata = userdata;
    }

    @Override
    public Map<String, FileNode> vhosts(Stage stage) {
        Map<String, FileNode> result;

        result = new HashMap<>();
        result.put(PREFIX + stage.getName(), editorDocroot(stage));
        return result;
    }

    @Override
    public void beforeStart(Stage stage) throws IOException {
        Ports ports;
        Session session;

        ports = stage.loadPortsOpt();
        userdata(stage);
        session = stage.session;
        editorDirectory(stage, ports.urlMap(!session.configuration.certificates.isEmpty(), session.configuration.vhosts, stage.config().suffix).values());
    }

    @Override
    public void beforeStop(Stage stage) throws IOException {
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) throws XmlException {
        String editorLocation;
        FileNode userdata;

        editorLocation = "http://" + fqdn(stage) + ":" + httpPort;
        userdata = stage.shared().join("editor/userdata/userdata.xml");
        if (host.startsWith(PustefixEditor.PREFIX)) {
            result.put("editor.userdata", userdata.getURI().toString());
            result.put("editor.locations", webinf.join("editor-locations.xml").getURI().toString());
        } else {
            result.put("editor.enabled", "true");
            result.put("editor.location", editorLocation);
            result.put("editor.secret", "foobar");
        }
    }

    //--

    private void userdata(Stage stage) throws IOException {
        FileNode dest;
        FileNode parent;
        String url;
        String status;

        url = Strings.removeRight(userdata, "/userdata.xml");
        dest = stage.shared().join("editor/userdata");
        if (dest.exists() && dest.getLastModified() < StageConfiguration.file(stage.wrapper).getLastModified()) {
            if (!url.equals(stage.session.subversion().checkoutUrl(dest))) {
                stage.session.console.verbose.println("config change detected - reloading userdata");
                status = stage.session.subversion().status(dest).trim();
                if (!status.isEmpty()) {
                    throw new IOException("cannot reload userdata: checkout has modifications");
                }
                dest.deleteTree();
            }
        }
        if (!dest.exists()) {
            parent = dest.getParent();
            parent.mkdirsOpt();
            try {
                // file name has to be userdata.xml, other names are currently not supported
                stage.session.subversion().checkout(parent, url, dest.getName(), stage.session.console.verbose);
            } catch (Failure e) {
                throw new IOException("cannot checkout editor userdata: " + e.getMessage(), e);
            }
        } else {
            // TODO: check url changes
        }
    }

    private void editorDirectory(Stage stage, Collection<String> apps) throws IOException {
        FileNode war;
        FileNode dest;
        List<String> lines;

        dest = editorDocroot(stage);
        if (dest.exists() && dest.getLastModified() < StageConfiguration.file(stage.wrapper).getLastModified()) {
            stage.session.console.verbose.println("config change detected - rebuilding editor war");
            dest.deleteTree();
        }
        if (!dest.exists()) {
            dest.mkdirs();
            try {
                war = stage.maven().resolve("org.pustefixframework.editor", "pustefix-editor-webui", "war", version);
            } catch (ArtifactResolutionException e) {
                throw new IOException("Cannot download editor: " + e.getMessage(), e);
            }
            war.unjar(dest);
            Files.stoolDirectory(dest.join("WEB-INF"));
        }
        // always update application list
        lines = new ArrayList<>();
        lines.add("<?xml version='1.0' encoding='utf-8' ?>");
        lines.add("<projects>");
        for (String app : apps) {
            lines.add("  <project>");
            lines.add("    <location>" + app + "</location>");
            lines.add("    <secret>foobar</secret>");
            lines.add("  </project>");
        }
        lines.add("</projects>");
        dest.join("WEB-INF/editor-locations.xml").writeLines(lines);
    }

    private FileNode editorDocroot(Stage stage) {
        return stage.shared().join("editor/webapp");
    }

    private String fqdn(Stage stage) {
        String result;

        result = stage.session.configuration.hostname;
        if (stage.session.configuration.vhosts) {
            result = PREFIX + stage.getName() + result;
        }
        return result;
    }
}
