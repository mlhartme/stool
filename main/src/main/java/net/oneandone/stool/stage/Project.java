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

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.scm.Scm;
import net.oneandone.stool.templates.TemplateField;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.Info;
import net.oneandone.stool.util.Macros;
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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
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

    /** @return nummer of applications */
    public abstract int size() throws IOException;

    public abstract List<String> faultProjects() throws IOException;

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

    private Macros lazyMacros;

    public Macros macros() {
        if (lazyMacros == null) {
            lazyMacros = new Macros();
            lazyMacros.addAll(stage.session.configuration.macros);
            lazyMacros.add("directory", getDirectory().getAbsolute());
            lazyMacros.add("localRepository", stage.session.localRepository().getAbsolute());
            lazyMacros.add("svnCredentials", Separator.SPACE.join(stage.session.svnCredentials().svnArguments()));
            lazyMacros.add("stoolSvnCredentials", stage.session.svnCredentials().stoolSvnArguments());
        }
        return lazyMacros;
    }

    public abstract boolean refreshPending(Console console) throws IOException;

    public void restoreFromBackup(Console console) throws IOException {
        console.info.println("Nothing to restore.");
    }

    public void executeRefresh(Console console) throws IOException {
        launcher(Strings.toArray(Separator.SPACE.split(macros().replace(stage.config().refresh)))).exec(console.info);
    }

    //--

    public void tuneConfiguration() throws IOException {
        if (stage.config().memory == 0 || stage.config().memory == 400) {
            stage.config().memory = Math.min(4096, 200 + size() * stage.session.configuration.baseMemory);
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
        stage.session.console.verbose.println("profiles: " + profiles);
        stage.session.console.verbose.println("userProperties: " + userProperties);
        warProjects(rootPom, userProperties, profiles, wars);
        if (wars.size() == 0) {
            throw new IOException("no war projects");
        }
        return wars;
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
    private void warProjects(FileNode pomXml, Properties userProperties, List<String> profiles, List<MavenProject> result) throws IOException {
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

    public int diskUsed() throws IOException {
        return used(directory);
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
        DOWN, UP, WORKING;

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
        used = diskUsed() + stage.containerDiskUsed();
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
        fields.addAll(TemplateField.scanTemplate(this, stage.config().template));
        return fields;
    }
}
