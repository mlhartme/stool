/**
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

import net.oneandone.maven.embedded.Maven;
import net.oneandone.stool.Chown;
import net.oneandone.stool.Start;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.configuration.Until;
import net.oneandone.stool.stage.artifact.Changes;
import net.oneandone.stool.util.BuildStats;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Host;
import net.oneandone.stool.util.KeyStore;
import net.oneandone.stool.util.Macros;
import net.oneandone.stool.util.OwnershipException;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.ServerXml;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.Subversion;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.ModeException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.transfer.TransferListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Concrete implementations are SourceStage or ArtifactStage.
 */
public abstract class Stage {
    public static Stage load(Session session, FileNode wrapper) throws IOException {
        return load(session, session.loadStageConfiguration(wrapper), wrapper, (FileNode) Stage.anchor(wrapper).resolveLink());
    }

    public static Stage load(Session session, FileNode wrapper, FileNode directory) throws IOException {
        return load(session, session.loadStageConfiguration(wrapper), wrapper, directory);
    }

    private static Stage load(Session session, StageConfiguration configuration, FileNode wrapper, FileNode directory) throws IOException {
        Stage result;
        String url;

        url = probe(session.subversion(), directory);
        if (url == null) {
            throw new IOException("cannot determine stage url: " + directory);
        }
        result = createOpt(session, url, configuration, wrapper, directory);
        if (result == null) {
            throw new IOException("unknown stage type: " + directory);
        }
        return result;
    }

    public static Stage createOpt(Session session, String url, StageConfiguration configuration, FileNode wrapper,
                                FileNode directory) throws IOException {
        if (configuration == null) {
            throw new IllegalArgumentException();
        }
        directory.checkDirectory();
        if (url.startsWith("gav:")) {
            return new ArtifactStage(session, url, wrapper, directory, configuration);
        }
        if (directory.join(configuration.pom).exists()) {
            return SourceStage.forLocal(session, wrapper, directory, configuration);
        }
        return null;
    }

    /** @return stage url or null if not a stage */
    public static String probe(Subversion subversion, FileNode directory) throws IOException {
        Node artifactGav;

        directory.checkDirectory();
        artifactGav = ArtifactStage.gavFile(directory);
        if (artifactGav.exists()) {
            return artifactGav.readString().trim();
        }
        try {
            return subversion.checkoutUrl(directory);
        } catch (Failure e) {
            // not a working copy or working-copy version is not readable with out svn version
            return null;
        }
    }

    public static FileNode anchor(FileNode wrapper) {
        return wrapper.join("anchor");
    }

    public static FileNode shared(FileNode wrapper) {
        return wrapper.join("shared");
    }

    //--

    public final Session session;

    //-- main methods
    protected final String url;
    /**
     * Contains two types of files:
     * * stool files:
     *   generated by stool, owned by the current user. Theses nodes get shared permissions
     * * runtime files:
     *   generated by tomcat, owned by the stage owner. These nodes get normal permissions of the owner
     */
    public FileNode wrapper;
    /** user visible directory */
    protected FileNode directory;
    protected final StageConfiguration configuration;
    /** lazy loading*/
    private BuildStats lazyBuildstats;
    private Maven maven;

    //--

    public Stage(Session session, String url, FileNode wrapper, FileNode directory, StageConfiguration configuration) {
        this.session = session;
        this.url = url;
        this.wrapper = wrapper;
        this.directory = directory;
        this.configuration = configuration;
    }
    /** Symlink to directory */
    public FileNode anchor() {
        return anchor(wrapper);
    }
    public FileNode shared() {
        return shared(wrapper);
    }
    /** User visible name of the stage */
    public String getName() {
        return wrapper.getName();
    }
    public FileNode getWrapper() {
        return wrapper;
    }
    public FileNode getDirectory() {
        return directory;
    }
    public String getUrl() {
        return url;
    }
    public StageConfiguration config() {
        return configuration;
    }
    public String getType() {
        return getClass().getSimpleName().toLowerCase();
    }

    private KeyStore keystore(List<Host> hosts) throws IOException {
        KeyStore keyStore;
        FileNode sslDir;
        String hostname;

        if (session.configuration.certificates.isEmpty()) {
            return null;
        }
        sslDir = shared().join("ssl");
        keyStore = new KeyStore(sslDir);
        if (!keyStore.exists()) {
            if (config().sslUrl != null && !config().sslUrl.isEmpty()) {
                keyStore.download(session.configuration.certificates, config().sslUrl);
            } else if (session.configuration.vhosts) {
                hostname = "*." + getName() + "." + session.configuration.hostname;
                keyStore.download(session.configuration.certificates, hostname);
            } else {
                keyStore.download(session.configuration.certificates, session.configuration.hostname);
            }
        }
        return keyStore;
    }

    public abstract boolean updateAvailable();

    /**
     * @return name "." first part of the domain
     */
    public String getMachine() {
        String machine;
        int idx;

        machine = session.configuration.hostname;
        idx = machine.indexOf('.');
        if (idx != -1) {
            machine = machine.substring(0, idx);
        }
        return getName() + "." + machine;
    }
    /**
     * technical state
     */
    public String technicalOwner() throws ModeException {

        return directory.getOwner().getName();
    }

    /**
     * external state - without *
     */
    //TODO rename
    public String ownerOverview() throws IOException {
        if (hijackedByOverview()) {
            return ownerBeforeHijacking();
        }
        return technicalOwner();
    }

    /**
     * external state
     */
    public String owner() throws IOException {
        if (hijackedByOverview()) {
            return ownerBeforeHijacking() + " *";
        }
        return technicalOwner();
    }

    //-- pid file handling
    public boolean isWorking() {
        return isLocked();
    }
    public State state() throws IOException {
        if (session.bedroom.stages().contains(getName())) {
            return State.SLEEPING;
        } else if (runningTomcat() != null) {
            return State.UP;
        } else {
            return State.DOWN;
        }

    }
    public boolean isLocked() {
        return shared().join("stage.aquire").exists();
    }

    public String runningTomcat() throws IOException {
        return readOpt(tomcatPidFile());
    }

    /** @return pid or null */
    public FileNode tomcatPidFile() throws IOException {
        return runFile("tomcat.pid");
    }

    //--

    /** @return pid or null */
    private FileNode runFile(String name) throws IOException {
        return shared().join("run", name);
    }

    /** @return vhost to docroot mapping, where vhost is artifactId + "." + stageName, to uniquely identify the host */
    protected abstract Map<String, FileNode> hosts() throws IOException;

    public Map<String, FileNode> selectedHosts() throws IOException {
        Map<String, FileNode> hosts;
        Iterator<Map.Entry<String, FileNode>> iter;
        List<String> selected;
        String hostname;

        hosts = hosts();
        selected = configuration.tomcatSelect;
        if (!selected.isEmpty()) {
            iter = hosts.entrySet().iterator();
            while (iter.hasNext()) {
                hostname = iter.next().getKey();
                hostname = hostname.substring(0, hostname.indexOf('.'));
                if (!selected.contains(hostname)) {
                    iter.remove();
                }
            }
        }
        return hosts;
    }

    public Ports loadPortsOpt() throws IOException {
        return Ports.loadOpt(this);
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
        return ports == null ? new HashMap<String, String>()
                : ports.urlMap(!session.configuration.certificates.isEmpty(), session.configuration.vhosts, config().suffix);
    }


    /** @return null when not supported. Otherwise, file must not be null, but does not have to exist. */
    public abstract List<DefaultArtifact> scanWars() throws IOException;

    public abstract String getDefaultBuildCommand();

    public FileNode catalinaPid() {
        return shared().join("run/tomcat.pid");
    }
    protected FileNode catalinaHome() {
        return session.home.join("tomcat", Start.tomcatName(configuration.tomcatVersion));
    }

    //-- tomcat helper

    public void start(Console console, Ports ports) throws Exception {
        ServerXml serverXml;
        String pidFile;
        KeyStore keystore;

        checkMemory();
        console.info.println("starting tomcat ...");

        // TODO workspace stages
        // FileNode editorLocations = directory.join("tomcat/editor/WEB-INF/editor-locations.xml");
        // if (editorLocations.exists()) {
        //    editorLocations.writeString(editorLocations.readString().replace("8080", Integer.toString(configuration.ports.tomcatHttp())));
        //    Files.stoolFile(editorLocations);
        // }

        serverXml = ServerXml.load(serverXmlTemplate());
        keystore = keystore(ports.hosts());
        serverXml.configure(ports, keystore, config().mode, config().cookies, session.configuration.vhosts,
                shared().join("editor/userdata/userdata.xml"));
        serverXml.save(serverXml());
        if (config().pustefixEditor) {
            userdata(console);
            editorDirectory(ports.urlMap(keystore != null, session.configuration.vhosts, configuration.suffix).values());
        }
        if (session.configuration.security.isLocal()) {
            catalinaBase().join("conf/Catalina").deleteTreeOpt().mkdir();
            // else: will be deleted by stool-catalina.sh -- with proper permissions
        }

        catalina("start").exec(console.verbose);
        pidFile = runningTomcat();
        if (pidFile == null) {
            throw new IOException("tomcat startup failed - no pid file found");
        }
        console.info.println("Applications available:");
        for (String app : namedUrls()) {
            console.info.println("  " + app);
        }
    }

    /** Fails if Tomcat is not running */
    public void stop(Console console) throws IOException {
        FileNode file;

        console.info.println("stopping tomcat ...");
        if (runningTomcat() == null) {
            throw new IOException("tomcat is not running.");
        }
        catalina("stop", "-force").exec(console.verbose);
        if (configuration.tomcatVersion.startsWith("6.")) {
            file = catalinaPid();
            file.deleteFile();
            console.info.println("removed stale " + file);
        }
    }

    /**
     * Wrapper for catalina.sh XOR stool-catalina.sh by ITOSHA.
     * action: start | stop -force
     */
    private Launcher catalina(String ... action) throws IOException {
        Launcher launcher;

        launcher = new Launcher(getDirectory());
        if (session.configuration.security.isShared()) {
            launcher.arg("sudo", session.bin("stool-catalina.sh").getAbsolute());
        } else {
            launcher.arg(session.bin("service-wrapper.sh").getAbsolute());
        }
        launcher.arg(catalinaHome().getAbsolute());
        launcher.arg(catalinaBase().getAbsolute());
        launcher.arg(session.home.join("service-wrapper", Start.serviceWrapperName(config().tomcatService)).getAbsolute());
        launcher.arg(shared().join("conf/service-wrapper.conf").getAbsolute());
        launcher.arg(shared().join("run").getAbsolute());
        launcher.arg(action);
        return launcher;
    }

    private void checkMemory() throws IOException {
        int requested;

        requested = configuration.tomcatHeap + configuration.tomcatPerm;
        int unreserved = session.memUnreserved();
        if (requested > unreserved) {
            throw new ArgumentException("Cannot reserve memory:\n"
              + "  unreserved: " + unreserved + "\n"
              + "  requested: " + requested + "\n"
              + "Consider stopping stages.");
        }
    }

    public FileNode catalinaBase() {
        return shared().join("tomcat");
    }

    public FileNode serverXml() {
        return catalinaBase().join("conf", "server.xml");
    }

    public FileNode serverXmlTemplate() {
        return catalinaBase().join("conf", "server.xml.template");
    }

    //--

    public void move(FileNode newDirectory) throws IOException {
        FileNode anchor;

        anchor = anchor();
        anchor.deleteDirectory();
        directory.move(newDirectory);
        directory = newDirectory;
        directory.link(anchor);
    }

    //--

    @Override
    public String toString() {
        return getType() + " " + url;
    }

    //-- util

    public void checkStopped() throws IOException {
        if (state() == State.UP) {
            throw new IOException("stage is not stopped.");
        }
    }

    public void checkOwnership() throws IOException, OwnershipException {
        if (hijackedByOverview() && owner().equals(session.user)) {
            session.console.info.println("The stage is currently owned by the overview. Going to own it to you back.");
            new Chown(session, true);
        }
        if (!technicalOwner().equals(session.user)) {
            throw new OwnershipException("Only the owner of the stage is allowed to to do this.\n"
              + "Just own the stage via 'stool chown' and try again.");
        }
    }


    public Launcher launcher(String... command) throws ModeException {
        return launcher(directory, command);
    }

    public Launcher launcher(FileNode working, String... command) throws ModeException {
        Launcher launcher;

        launcher = new Launcher(working, command);
        session.environment(this).save(launcher);
        return launcher;
    }

    //TODO: Maybe rename
    public void prepareRefresh(Console console) throws IOException {
    }

    public void restoreFromBackup(Console console) throws IOException {
        console.info.println("Nothing to restore.");
    }

    public void executeRefresh(Console console) throws IOException {
        launcher(Strings.toArray(Separator.SPACE.split(config().refresh))).exec(console.info);
    }

    public void refresh(Console console, boolean forcePrepare) throws IOException {
        if (forcePrepare) {
            prepareRefresh(console);
        }
        executeRefresh(console);
    }


    //--

    public void tuneConfiguration() throws IOException {
        List<DefaultArtifact> tmp;
        int wars;

        tmp = scanWars();
        if (tmp == null) {
            wars = 4;
        } else {
            wars = tmp.size();
        }
        if (configuration.tomcatHeap == 0 || configuration.tomcatHeap == 200) {
            configuration.tomcatHeap = Math.min(4096, 150 + wars * session.configuration.baseHeap);
        }
        if (configuration.tomcatPerm == 0 || configuration.tomcatPerm == 64) {
            configuration.tomcatPerm = Math.min(1024, 100 + wars * session.configuration.basePerm);
        }
        if (configuration.build.isEmpty() || configuration.build.equals("false")) {
            configuration.build = getDefaultBuildCommand();
        }

        if (session.configuration.security.isLocal()) {
            configuration.until = Until.reserved();
        } else {
            configuration.until = Until.withOffset(8);
        }

    }

    public void saveWrapper() throws IOException {
        Files.stoolDirectory(shared(wrapper).mkdirsOpt());
        session.saveStageProperties(configuration, wrapper);
    }

    //--

    public void setMaven(Maven maven) {
        this.maven = maven;
    }

    /** CAUTION: this is not a session method, because it respected the stage repository */
    public Maven maven() throws IOException {
        World world;

        if (maven == null) {
            world = session.console.world;
            // CAUTION: shared plexus - otherwise, Maven components are created over and over again
            maven = Maven.withSettings(world, localRepository(), world.file(config().mavenHome).join("conf/settings.xml"), null,
                    session.plexus(), null, null);
            // always get the latest snapshots
            maven.getRepositorySession().setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        }
        return maven;
    }

    protected List<MavenProject> loadWars(FileNode rootPom) throws IOException {
        List<MavenProject> wars;
        List<String> profiles;
        Properties userProperties;

        wars = new ArrayList<>();
        profiles = new ArrayList<>();
        userProperties = new Properties();
        addProfilesAndProperties(userProperties, profiles, configuration.mavenOpts);
        addProfilesAndProperties(userProperties, profiles, getBuild());
        session.console.verbose.println("profiles: " + profiles);
        session.console.verbose.println("userProperties: " + userProperties);
        warProjects(rootPom, userProperties, profiles, wars);
        if (wars.size() == 0) {
            throw new IOException("no war projects");
        }
        return wars;
    }

    public String getBuild() {
        return macros().replace(configuration.build);
    }

    private Macros lazyMacros = null;

    public Macros macros() {
        if (lazyMacros == null) {
            lazyMacros = new Macros();
            lazyMacros.addAll(session.configuration.macros);
            lazyMacros.add("directory", getDirectory().getAbsolute());
            lazyMacros.add("localRepository", localRepository().getAbsolute());
            lazyMacros.add("proxyOpts", session.environment.proxyOpts(false));
        }
        return lazyMacros;
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
            root = maven().loadPom(pomXml, false, userProperties, profiles, null);
        } catch (ProjectBuildingException | RepositoryException e) {
            throw new IOException("cannot parse " + pomXml + ": " + e.getMessage(), e);
        }
        session.console.verbose.println("loading " + pomXml);
        if ("war".equals(root.getPackaging())) {
            result.add(root);
        } else {
            for (String module : root.getModules()) {
                modulePom = session.console.world.file(root.getBasedir()).join(module);
                if (modulePom.isDirectory()) {
                    modulePom = modulePom.join("pom.xml");
                }
                warProjects(modulePom, userProperties, profiles, result);
            }
        }
    }
    public BuildStats buildStats() throws IOException {
        if (lazyBuildstats == null) {
            FileNode file;

            file = shared().join("buildstats.json");
            if (file.exists()) {
                lazyBuildstats = BuildStats.load(file);
            } else {
                lazyBuildstats = new BuildStats(file);
            }
        }
        return lazyBuildstats;
    }

    public boolean isOverview() {
        return false;
    }

    public Changes changes() throws IOException {
        return new Changes();
    }
    public FileNode localRepository() {
        return session.configuration.security.isLocal()
          ? (FileNode) session.console.world.getHome().join(".m2/repository") : wrapper.join(".m2");
    }
    protected FileNode warFile(MavenProject project) {
        Build build;

        build = project.getBuild();
        return session.console.world.file(build.getDirectory()).join(build.getFinalName() + ".war");
    }
    public boolean hijackedByOverview() {
        return shared().join(".hijacked").exists();
    }
    public String ownerBeforeHijacking() throws IOException {
        return shared().join(".hijacked").readString();
    }
    public Applogs logs() {
        return new Applogs(shared().join("applogs"));
    }

    public static enum State {
        DOWN, SLEEPING, UP, WORKING;

        public String toString() {
            return name().toLowerCase();
        }
    }

    //--

    /** @return pid or null */
    private static String readOpt(FileNode file) throws IOException {
        return file.exists() ? file.readString().trim() : null;
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
        } else {
            return nameForSvnUrl(url);
        }

    }

    public static String nameForGavUrl(String url) {
        int end;
        int start;

        end = url.lastIndexOf(':');
        if (end == -1) {
            return "stage";
        }
        start = url.lastIndexOf(':', end - 1);
        if (end == -1) {
            return "stage";
        }
        return url.substring(start + 1, end);
    }

    public static String nameForSvnUrl(String url) {
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
        return result.isEmpty() ? "stage" : result;
    }

    //-- Pustefix Editor ...

    public FileNode editorDocroot() throws IOException {
        return shared().join("editor/webapp");
    }

    private void editorDirectory(Collection<String> apps) throws IOException {
        FileNode war;
        FileNode dest;
        List<String> lines;

        dest = editorDocroot();
        if (dest.exists() && dest.getLastModified() < StageConfiguration.configurationFile(wrapper).getLastModified()) {
            session.console.verbose.println("config change detected - rebuilding editor war");
            dest.deleteTree();
        }
        if (!dest.exists()) {
            dest.mkdirs();
            try {
                war = maven().resolve("org.pustefixframework.editor", "pustefix-editor-webui", "war", config().pustefixEditorVersion);
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

    public void userdata(Console console) throws IOException {
        FileNode dest;
        FileNode parent;
        String url;
        String status;

        url = Strings.removeRight(config().pustefixEditorUserdata, "/userdata.xml");
        dest = shared().join("editor/userdata");
        if (dest.exists() && dest.getLastModified() < StageConfiguration.configurationFile(wrapper).getLastModified()) {
            if (!url.equals(session.subversion().checkoutUrl(dest))) {
                session.console.verbose.println("config change detected - reloading userdata");
                status = session.subversion().status(dest).trim();
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
                session.subversion().checkout(parent, url, dest.getName(), console.verbose);
            } catch (Failure e) {
                throw new IOException("cannot checkout editor userdata: " + e.getMessage(), e);
            }
        } else {
            // TODO: check url changes
        }
    }
}
