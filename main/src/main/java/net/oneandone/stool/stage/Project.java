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
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.scm.Scm;
import net.oneandone.stool.templates.TemplateField;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.Info;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.filter.Filter;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.FileNotFoundException;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concrete implementations are SourceProject or ArtifactProject.
 */
public class Project {
    public static Project forDirectory(Session session, String id, FileNode directory, StageConfiguration configuration)
            throws IOException {
        return new Project(session, Scm.checkoutUrl(directory), id, directory, configuration);
    }

    //--

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
        result = createOpt(session, id, configuration, directory);
        if (result == null) {
            throw new IOException("unknown stage type: " + directory);
        }
        return result;
    }

    /** @return stage url or null if not a stage */
    public static String probe(FileNode directory) throws IOException {
        directory.checkDirectory();
        return Scm.checkoutUrlOpt(directory);
    }

    public static Project createOpt(Session session, String id, StageConfiguration configuration, FileNode directory) throws IOException {
        if (configuration == null) {
            throw new IllegalArgumentException();
        }
        directory.checkDirectory();
        if (directory.join("pom.xml").exists()) {
            return Project.forDirectory(session, id, directory, configuration);
        }
        return null;
    }

    //--

    private final String origin;

    /** user visible directory */
    private FileNode directory;

    public final Stage stage;

    //--

    public Project(Session session, String origin, String id, FileNode directory, StageConfiguration configuration) {
        this.origin = origin;
        this.directory = directory;
        this.stage = new Stage(session, id, backstageDirectory(directory), configuration);
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

    /** @return vhost to docroot mapping, where vhost does *not* include the stage name */
    public Map<String, FileNode> vhosts() throws IOException {
        Map<String, FileNode> applications;

        applications = new LinkedHashMap<>();
        for (Map.Entry<String, FileNode> entry : wars().entrySet()) {
            applications.put(entry.getKey(), docroot(entry.getValue()));
        }
        return applications;
    }

    private FileNode docroot(FileNode war) throws IOException {
        FileNode basedir;
        List<FileNode> result;
        Filter filter;

        basedir = war.getParent().getParent();
        filter = basedir.getWorld().filter();
        filter.include("target/*/WEB-INF");
        filter.predicate((node, b) -> node.isDirectory() && (node.join("lib").isDirectory() || node.join("classes").isDirectory()));
        filter.exclude("target/test-classes/**/*");
        result = basedir.find(filter);
        switch (result.size()) {
            case 0:
                throw new IOException("No web application found. Did you build the project?");
            case 1:
                return result.get(0).getParent();
            default:
                throw new FileNotFoundException("web.xml ambiguous: " + result);
        }
    }

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
    public int size() throws IOException {
        return wars().size();
    }


    public List<String> faultProjects() throws IOException {
        List<String> result;

        result = new ArrayList<>();
        for (FileNode war : wars().values()) {
            result.add("file:" + war.getAbsolute());
        }
        return result;
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

    protected void addWars(FileNode directory, Map<String, FileNode> result) throws IOException {
        List<FileNode> files;
        List<FileNode> wars;

        files = directory.list();
        if (!hasPom(files)) {
            return;
        }

        wars = directory.find("target/*.war");
        switch (wars.size()) {
            case 0:
                // do nothing
                break;
            case 1:
                result.put(directory.getName(), wars.get(0));
                break;
            default:
                throw new IOException(directory + ": wars ambiguous: " + wars);
        }
        for (FileNode file : files) {
            if (file.isDirectory()) {
                addWars(file, result);
            }
        }
    }

    private static boolean hasPom(List<FileNode> list) {
        String name;

        for (FileNode file : list) {
            name = file.getName();
            if (name.equals("pom.xml") || name.equals("workspace.xml")) {
                return true;
            }
        }
        return false;
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

    public String buildtime() throws IOException {
        Collection<FileNode> artifacts;
        long time;

        artifacts = wars().values();
        if (artifacts.isEmpty()) {
            return null;
        }
        time = Long.MIN_VALUE;
        for (FileNode a : artifacts) {
            time = Math.max(time, a.getLastModified());
        }
        return FMT.format(Instant.ofEpochMilli(time));
    }

    private Map<String, FileNode> lazyWars;

    public Map<String, FileNode> wars() throws IOException {
        if (lazyWars == null) {
            lazyWars = new HashMap<>();
            addWars(directory, lazyWars);
        }
        return lazyWars;
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
