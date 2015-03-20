package net.oneandone.stool.extensions;

import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.xml.XmlException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PustefixEditor {
    private static final java.lang.String PREFIX = "cms.";

    public static PustefixEditor create(Stage stage) {
        return new PustefixEditor(stage, stage.config().pustefixEditor, stage.config().pustefixEditorVersion,
                stage.config().pustefixEditorUserdata);
    }

    //--

    private final Stage stage;

    private final boolean enabled;
    
    private final String version;

    private final String userdata;

    public PustefixEditor(Stage stage, boolean enabled, String version, String userdata) {
        this.stage = stage;
        this.enabled = enabled;
        this.version = version;
        this.userdata = userdata;
    }

    public void vhosts(Map<String, FileNode> hosts) {
        if (enabled) {
            hosts.put(PREFIX + stage.getName(), editorDocroot(stage));
        }
    }

    public void beforeStart(Collection<String> apps) throws IOException {
        if (enabled) {
            userdata(stage.session.console);
            editorDirectory(apps);
        }
    }

    public void contextParameter(int httpPort, String name, Element context, FileNode webinf) throws XmlException {
        String editorLocation;
        FileNode userdata;

        if (enabled) {
            editorLocation = "http://" + fqdn() + ":" + httpPort;
            userdata = stage.shared().join("editor/userdata/userdata.xml");
            if (name.startsWith(PustefixEditor.PREFIX)) {
                parameter(context, "editor.userdata").setAttribute("value", userdata.getURI().toString());
                parameter(context, "editor.locations").setAttribute("value", webinf.join("editor-locations.xml").getURI().toString());
            } else {
                parameter(context, "editor.enabled").setAttribute("value", "true");
                parameter(context, "editor.location").setAttribute("value", editorLocation);
                parameter(context, "editor.secret").setAttribute("value", "foobar");
            }
        }
    }

    //--

    private void userdata(Console console) throws IOException {
        FileNode dest;
        FileNode parent;
        String url;
        String status;

        url = Strings.removeRight(userdata, "/userdata.xml");
        dest = stage.shared().join("editor/userdata");
        if (dest.exists() && dest.getLastModified() < StageConfiguration.configurationFile(stage.wrapper).getLastModified()) {
            if (!url.equals(stage.session.subversion().checkoutUrl(dest))) {
                console.verbose.println("config change detected - reloading userdata");
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
                stage.session.subversion().checkout(parent, url, dest.getName(), console.verbose);
            } catch (Failure e) {
                throw new IOException("cannot checkout editor userdata: " + e.getMessage(), e);
            }
        } else {
            // TODO: check url changes
        }
    }

    private void editorDirectory(Collection<String> apps) throws IOException {
        FileNode war;
        FileNode dest;
        List<String> lines;

        dest = editorDocroot(stage);
        if (dest.exists() && dest.getLastModified() < StageConfiguration.configurationFile(stage.wrapper).getLastModified()) {
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

    private String fqdn() {
        String result;

        result = stage.session.configuration.hostname;
        if (stage.session.configuration.vhosts) {
            result = PREFIX + stage.getName() + result;
        }
        return result;
    }

    private Element parameterOpt(Element context, String name) throws XmlException {
        return stage.getDirectory().getWorld().getXml().getSelector().elementOpt(context, "Parameter[@name='" + name + "']");
    }

    private Element parameter(Element context, String name) throws XmlException {
        Element parameter;

        parameter = parameterOpt(context, name);
        if (parameter == null) {
            parameter = context.getOwnerDocument().createElement("Parameter");
            parameter.setAttribute("name", name);
            parameter.setAttribute("override", "false");
            context.appendChild(parameter);
        }
        return parameter;
    }
}
