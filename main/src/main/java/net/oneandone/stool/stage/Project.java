/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.stool.stage;

import com.google.gson.JsonObject;
import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.docker.Engine;
import net.oneandone.stool.scm.Scm;
import net.oneandone.stool.templates.TemplateField;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.Info;
import net.oneandone.stool.util.LogEntry;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.eclipse.aether.RepositoryException;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concrete implementations are SourceProject or ArtifactProject.
 */
public abstract class Project {
    public static FileNode backstageDirectory(FileNode projectDirectory) {
        return projectDirectory.join(".backstage");
    }

    //--

    public static Project load(Session session, FileNode backstageLink) throws IOException {
        FileNode backstageResolved;

        try {
            backstageResolved = backstageLink.resolveLink();
        } catch (IOException e) {
            throw new IOException("unknown stage id: " + backstageLink.getName(), e);
        }
        return load(session, session.loadStageConfiguration(backstageResolved), backstageLink.getName(), backstageResolved.getParent());
    }

    private static Project load(Session session, StageConfiguration configuration, String id, FileNode directory) throws IOException {
        Project result;
        String origin;

        origin = probe(directory);
        if (origin == null) {
            throw new IOException("cannot determine stage origin: " + directory);
        }
        result = createOpt(session, id, origin, configuration, directory);
        if (result == null) {
            throw new IOException("unknown stage type: " + directory);
        }
        return result;
    }

    /** @return stage url or null if not a stage */
    public static String probe(FileNode directory) throws IOException {
        Node artifactGav;

        directory.checkDirectory();
        artifactGav = ArtifactProject.urlFile(directory);
        if (artifactGav.exists()) {
            return artifactGav.readString().trim();
        }
        return Scm.checkoutUrlOpt(directory);
    }

    public static Project createOpt(Session session, String id, String origin, StageConfiguration configuration, FileNode directory) throws IOException {
        if (configuration == null) {
            throw new IllegalArgumentException();
        }
        directory.checkDirectory();
        if (origin.startsWith("gav:") || origin.startsWith("file:")) {
            return new ArtifactProject(session, origin, id, directory, configuration);
        }
        if (directory.join(configuration.pom).exists()) {
            return SourceProject.forLocal(session, id, directory, configuration);
        }
        return null;
    }

    //--

    protected final String origin;

    public final Stage stage;

    /** user visible directory */
    protected FileNode directory;

    //--

    public Project(Session session, String origin, String id, FileNode directory, StageConfiguration configuration) {
        this.origin = origin;
        this.stage = new Stage(session, id, backstageDirectory(directory), configuration);
        this.directory = directory;
    }

    public Stage getStage() {
        return stage;
    }
    public FileNode getDirectory() {
        return directory;
    }
    public String getOrigin() {
        return origin;
    }
    public String getType() {
        return getClass().getSimpleName().toLowerCase();
    }

    public abstract boolean updateAvailable();

    //--

    public abstract List<String> vhostNames() throws IOException;

    /** @return vhost to docroot mapping, where vhost does *not* include the stage name */
    public abstract Map<String, FileNode> vhosts() throws IOException;

    public Map<String, FileNode> selectedVhosts() throws IOException {
        Map<String, FileNode> vhosts;
        Iterator<Map.Entry<String, FileNode>> iter;
        List<String> selected;
        String vhostname;

        vhosts = vhosts();
        selected = stage.config().select;
        if (!selected.isEmpty()) {
            iter = vhosts.entrySet().iterator();
            while (iter.hasNext()) {
                vhostname = iter.next().getKey();
                if (!selected.contains(vhostname)) {
                    iter.remove();
                }
            }
        }
        return vhosts;
    }

    public Ports loadPortsOpt() throws IOException {
        return stage.session.pool().stageOpt(stage.getId());
    }

    /** @return empty list of no ports are allocated */
    public List<String> namedUrls() throws IOException {
        List<String> result;

        result = new ArrayList<>();
        for (Map.Entry<String, String> entry : urlMap().entrySet()) {
            result.add(entry.getKey() + " " + entry.getValue());
        }
        return result;
    }

    /** @return empty map of no ports are allocated */
    public Map<String, String> urlMap() throws IOException {
        Ports ports;

        ports = loadPortsOpt();
        return ports == null ? new HashMap<>() : ports.urlMap(stage.getName(), stage.session.configuration.hostname, stage.config().url);
    }

    /** @return nummer of applications */
    public abstract int size() throws IOException;

    public abstract String getDefaultBuildCommand();

    /* TODO: work for tomcat only */
    public void awaitStartup(Console console) throws IOException, InterruptedException {
        Ports ports;
        String state;

        ports = loadPortsOpt();
        for (int count = 0; true; count++) {
            try {
                state = jmxEngineState(ports);
                break;
            } catch (Exception e) {
                if (count > 40) {
                    throw new IOException("initial state timed out: " + e.getMessage(), e);
                }
                Thread.sleep(50);
            }
        }
        for (int count = 1; !"STARTED".equals(state); count++) {
            if (count > 10 * 60 *5) {
                throw new IOException("tomcat startup timed out, state" + state);
            }
            if (count % 100 == 99) {
                console.info.println("waiting for tomcat startup ... " + state);
            }
            Thread.sleep(100);
            state = jmxEngineState(ports);
        }
    }

    private MBeanServerConnection lazyJmxConnection;

    private MBeanServerConnection jmxConnection(Ports ports) throws IOException {
        if (lazyJmxConnection == null) {
            JMXServiceURL url;

            // see https://docs.oracle.com/javase/tutorial/jmx/remote/custom.html
            try {
                url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + stage.session.configuration.hostname + ":" + ports.jmx() + "/jmxrmi");
            } catch (MalformedURLException e) {
                throw new IllegalStateException(e);
            }
            lazyJmxConnection = JMXConnectorFactory.connect(url, null).getMBeanServerConnection();
        }
        return lazyJmxConnection;
    }

    private String jmxEngineState(Ports ports) throws IOException {
        MBeanServerConnection connection;
        ObjectName name;

        connection = jmxConnection(ports);
        try {
            name = new ObjectName("Catalina:type=Engine");
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
        try {
            return (String) connection.getAttribute(name, "stateName");
        } catch (ReflectionException | InstanceNotFoundException | AttributeNotFoundException | MBeanException e) {
            throw new IllegalStateException();
        }
    }


    public abstract List<String> faultProjects() throws IOException;

    // CAUTION: blocks until ctrl-c.
    // Format: https://docs.docker.com/engine/api/v1.33/#operation/ContainerAttach
    public void tailF(PrintWriter dest) throws IOException {
        Engine engine;

        engine = stage.session.dockerEngine();
        engine.containerLogsFollow(stage.dockerContainer(), new OutputStream() {
            @Override
            public void write(int b) {
                dest.write(b);
                if (b == 10) {
                    dest.flush(); // newline
                }
            }
        });
    }


    /** Fails if container is not running */
    public void stop(Console console) throws IOException {
        String container;
        Engine engine;

        container = stage.dockerContainer();
        if (container == null) {
            throw new IOException("container is not running.");
        }
        console.info.println("stopping container ...");
        engine = stage.session.dockerEngine();
        engine.containerStop(container, 300);
        stage.dockerContainerFile().deleteFile();
    }

    //--

    public void move(FileNode newDirectory) throws IOException {
        FileNode link;

        link = stage.session.backstageLink(stage.getId());
        link.deleteTree();
        directory.move(newDirectory);
        directory = newDirectory;
        backstageDirectory(directory).link(link);
    }

    //--

    @Override
    public String toString() {
        return getType() + " " + origin;
    }

    //-- util

    /** @return launcher with build environment */
    public Launcher launcher(String... command) {
        return launcher(directory, command);
    }

    public Launcher launcher(FileNode working, String... command) {
        Launcher launcher;

        launcher = new Launcher(working, command);
        stage.session.environment(this).save(launcher);
        return launcher;
    }

    public abstract boolean refreshPending(Console console) throws IOException;

    public void restoreFromBackup(Console console) throws IOException {
        console.info.println("Nothing to restore.");
    }

    public void executeRefresh(Console console) throws IOException {
        launcher(Strings.toArray(Separator.SPACE.split(stage.macros().replace(stage.config().refresh)))).exec(console.info);
    }

    //--

    public void tuneConfiguration() throws IOException {
        if (stage.config().memory == 0 || stage.config().memory == 400) {
            stage.config().memory = Math.min(4096, 200 + size() * stage.session.configuration.baseMemory);
        }
        if (stage.config().build.isEmpty() || stage.config().build.equals("false")) {
            stage.config().build = getDefaultBuildCommand();
        }
    }

    public void initialize() throws IOException {
        // important: this is the last step in stage creation; creating this file indicates that the stage is ready
        stage.session.saveStageProperties(stage.config(), stage.directory);
    }

    //--

    protected List<MavenProject> loadWars(FileNode rootPom) throws IOException {
        List<MavenProject> wars;
        List<String> profiles;
        Properties userProperties;

        wars = new ArrayList<>();
        profiles = new ArrayList<>();
        userProperties = new Properties();
        addProfilesAndProperties(userProperties, profiles, stage.config().mavenOpts);
        addProfilesAndProperties(userProperties, profiles, getBuild());
        stage.session.console.verbose.println("profiles: " + profiles);
        stage.session.console.verbose.println("userProperties: " + userProperties);
        warProjects(rootPom, userProperties, profiles, wars);
        if (wars.size() == 0) {
            throw new IOException("no war projects");
        }
        return wars;
    }

    public String getBuild() {
        return stage.macros().replace(stage.config().build);
    }

    public boolean isCommitted() throws IOException {
        if (this instanceof ArtifactProject) {
            return true;
        }
        return stage.session.scm(getOrigin()).isCommitted(this);
    }

    private void addProfilesAndProperties(Properties userProperties, List<String> profiles, String args) {
        int idx;

        for (String part : Separator.SPACE.split(args)) {
            if (part.startsWith("-P")) {
                profiles.add(part.substring(2));
            }
            if (part.startsWith("-D")) {
                part = part.substring(2);
                idx = part.indexOf('=');
                if (idx == -1) {
                    userProperties.put(part, "");
                } else {
                    userProperties.put(part.substring(0, idx), part.substring(idx + 1));
                }
            }
        }
    }
    private void warProjects(FileNode pomXml, Properties userProperties, List<String> profiles,
      List<MavenProject> result) throws IOException {
        MavenProject root;
        FileNode modulePom;

        try {
            root = stage.maven().loadPom(pomXml, false, userProperties, profiles, null);
        } catch (ProjectBuildingException | RepositoryException e) {
            throw new IOException("cannot parse " + pomXml + ": " + e.getMessage(), e);
        }
        stage.session.console.verbose.println("loading " + pomXml);
        if ("war".equals(root.getPackaging())) {
            result.add(root);
        } else {
            for (String module : root.getModules()) {
                modulePom = stage.session.world.file(root.getBasedir()).join(module);
                if (modulePom.isDirectory()) {
                    modulePom = modulePom.join("pom.xml");
                }
                warProjects(modulePom, userProperties, profiles, result);
            }
        }
    }

    public static String timespan(long since) {
        long diff;
        StringBuilder result;
        long hours;

        diff = System.currentTimeMillis() - since;
        diff /= 1000;
        hours = diff / 3600;
        if (hours >= 48) {
            return (hours / 24) + " days";
        } else {
            result = new StringBuilder();
            new Formatter(result).format("%d:%02d:%02d", hours, diff % 3600 / 60, diff % 60);
            return result.toString();
        }
    }

    public int diskUsed() throws IOException {
        return used(directory);
    }

    public int containerDiskUsed() throws IOException {
        String container;
        JsonObject obj;

        container = stage.dockerContainer();
        if (container == null) {
            return 0;
        }
        obj = stage.session.dockerEngine().containerInspect(container, true);
        // not SizeRootFs, that's the image size plus the rw layer
        return (int) (obj.get("SizeRw").getAsLong() / (1024 * 1024));
    }

    /** @return megabytes */
    private static int used(FileNode dir) throws IOException {
        Path path;

        path = dir.toPath();
        final AtomicLong size = new AtomicLong(0);
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                size.addAndGet(attrs.size());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                // TODO: hard-wired fault dependency
                if (file.endsWith(".backstage/fault") && e instanceof java.nio.file.AccessDeniedException) {
                    return FileVisitResult.CONTINUE;
                } else {
                    throw e;
                }
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                if (e != null) {
                    throw e;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return (int) (size.get() / (1024 * 1024));
    }

    public static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public abstract List<FileNode> artifacts() throws IOException;

    public String buildtime() throws IOException {
        Collection<FileNode> artifacts;
        long time;

        artifacts = artifacts();
        if (artifacts.isEmpty()) {
            return null;
        }
        time = Long.MIN_VALUE;
        for (FileNode a : artifacts) {
            time = Math.max(time, a.getLastModified());
        }
        return FMT.format(Instant.ofEpochMilli(time));
    }

    public enum State {
        DOWN, SLEEPING, UP, WORKING;

        public String toString() {
            return name().toLowerCase();
        }
    }

    //-- stage name

    /**
     * The stage name has to be a valid domain name because is used as part of the application url.
     * See http://tools.ietf.org/html/rfc1035 section 2.3.1.
     */
    public static void checkName(String name) {
        char c;

        if (name.isEmpty()) {
            throw new ArgumentException("empty stage name is not allowed");
        }
        if (name.length() > 30) {
            //ITCA does not accept too long commonNames
            throw new ArgumentException("Stage Name is too long. Please take a shorter one.");
        }
        if (!isLetter(name.charAt(0))) {
            throw new ArgumentException("stage name does not start with a letter");
        }
        for (int i = 1; i < name.length(); i++) {
            c = name.charAt(i);
            if (!isValidStageNameChar(c)) {
                throw new ArgumentException("stage name contains illegal character: " + c);
            }
        }
    }

    public static boolean isValidStageNameChar(char c) {
        return isLetter(c) || isDigit(c) || c == '-' || c == '.';
    }
    // cannot use Character.is... because we check ascii only
    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
    // cannot use Character.is... because we check ascii only
    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static String nameForUrl(String url) {
        if (url.startsWith("gav:")) {
            return nameForGavUrl(url);
        } else if (url.startsWith("file:")) {
            return nameForFileUrl(url);
        } else {
            return nameForSvnOrGitUrl(url);
        }
    }

    private static String nameForGavUrl(String url) {
        int end;
        int start;

        url = one(url);
        end = url.lastIndexOf(':');
        if (end == -1) {
            return "stage";
        }
        start = url.lastIndexOf(':', end - 1);
        if (start == -1) {
            return "stage";
        }
        return url.substring(start + 1, end);
    }

    private static String nameForFileUrl(String url) {
        int idx;

        url = one(url);
        idx = url.lastIndexOf('/');
        if (idx == -1) {
            return "idx";
        }
        url = url.substring(idx + 1);
        idx = url.lastIndexOf('.');
        if (idx == -1) {
            return url;
        } else {
            return url.substring(0, idx);
        }
    }

    private static String one(String url) {
        int end;

        end = url.lastIndexOf(',');
        if (end != -1) {
            url = url.substring(0, end);
        }
        end = url.lastIndexOf('=');
        if (end != -1) {
            url = url.substring(0, end);
        }
        return url;
    }

    private static String nameForSvnOrGitUrl(String url) {
        String result;
        int idx;

        result = Strings.removeRightOpt(url, "/");
        idx = result.indexOf(':');
        if (idx != -1) {
            // strip protocol - important vor gav stages
            result = result.substring(idx + 1);
        }
        result = Strings.removeRightOpt(result, "/trunk");
        idx = result.lastIndexOf('/');
        result = result.substring(idx + 1); // ok for -1
        result = Strings.removeRightOpt(result, ".git");
        return result.isEmpty() ? "stage" : result;
    }

    //--

    public void checkConstraints() throws IOException {
        int used;
        int quota;

        if (stage.config().expire.isExpired()) {
            throw new ArgumentException("Stage expired " + stage.config().expire + ". To start it, you have to adjust the 'expire' date.");
        }
        quota = stage.config().quota;
        used = diskUsed() + containerDiskUsed();
        if (used > quota) {
            throw new ArgumentException("Stage quota exceeded. Used: " + used + " mb  >  quota: " + quota + " mb.\n" +
                    "Consider running 'stool cleanup'.");
        }
    }

    //--

    public Field fieldOpt(String str) throws IOException {
        for (Field f : fields()) {
            if (str.equals(f.name())) {
                return f;
            }
        }
        return null;
    }

    public List<Info> fieldsAndName() throws IOException {
        List<Info> result;

        result = new ArrayList<>();
        result.add(stage.propertyOpt("name"));
        result.addAll(fields());
        return result;
    }

    //--

    public Info info(String str) throws IOException {
        Info result;
        List<String> lst;

        result = stage.propertyOpt(str);
        if (result != null) {
            return result;
        }
        result = fieldOpt(str);
        if (result != null) {
            return result;
        }
        lst = new ArrayList<>();
        for (Field f : fields()) {
            lst.add(f.name());
        }
        for (Property p : stage.properties()) {
            lst.add(p.name());
        }
        throw new ArgumentException(str + ": no such status field or property, choose one of " + lst);
    }

    public List<Field> fields() throws IOException {
        List<Field> fields;

        fields = stage.fields();
        fields.add(new Field("selected") {
            @Override
            public Object get() throws IOException {
                return stage.session.isSelected(Project.this);
            }
        });
        fields.add(new Field("directory") {
            @Override
            public Object get() {
                return Project.this.directory.getAbsolute();
            }
        });
        fields.add(new Field("origin") {
            @Override
            public Object get() {
                return Project.this.getOrigin();
            }
        });
        fields.add(new Field("type") {
            @Override
            public Object get() {
                return Project.this.getType();
            }
        });
        fields.add(new Field("buildtime") {
            @Override
            public Object get() throws IOException {
                return Project.this.buildtime();
            }
        });
        fields.add(new Field("disk") {
            @Override
            public Object get() throws IOException {
                return Project.this.diskUsed();
            }
        });
        fields.add(new Field("container-disk") {
            @Override
            public Object get() throws IOException {
                return Project.this.containerDiskUsed();
            }
        });
        fields.add(new Field("uptime") {
            @Override
            public Object get() throws IOException {
                String container;

                container = Project.this.stage.dockerContainer();
                return container == null ? null : timespan(Project.this.stage.session.dockerEngine().containerStartedAt(container));
            }
        });
        fields.add(new Field("apps") {
            @Override
            public Object get() throws IOException {
                return Project.this.namedUrls();
            }
        });
        fields.addAll(TemplateField.scanTemplate(this, stage.config().template));
        return fields;
    }

    //--

    public int contentHash() throws IOException {
        return ("StageInfo{"
                + "name='" + stage.config().name + '\''
                + ", id='" + stage.getId() + '\''
                + ", comment='" + stage.config().comment + '\''
                + ", origin='" + origin + '\''
                + ", urls=" + urlMap()
                + ", state=" + stage.state()
                + ", displayState=" + stage.displayState()
                + ", last-modified-by='" + stage.lastModifiedBy() + '\''
                + ", updateAvailable=" + updateAvailable()
                + '}').hashCode();
    }

    //-- for dashboard

    public String sharedText() throws IOException {
        Map<String, String> urls;

        urls = urlMap();
        if (urls == null) {
            return "";
        }
        String content;
        StringBuilder stringBuilder;
        stringBuilder = new StringBuilder("Hi, \n");
        for (String url : urls.values()) {
            stringBuilder.append(url).append("\n");
        }

        content = URLEncoder.encode(stringBuilder.toString(), "UTF-8");
        content = content.replace("+", "%20").replaceAll("\\+", "%20")
                .replaceAll("\\%21", "!")
                .replaceAll("\\%27", "'")
                .replaceAll("\\%28", "(")
                .replaceAll("\\%29", ")")
                .replaceAll("\\%7E", "~");

        return content;
    }

}
