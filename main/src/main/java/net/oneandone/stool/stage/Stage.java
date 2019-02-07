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

import net.oneandone.inline.Console;
import net.oneandone.maven.embedded.Maven;
import net.oneandone.stool.configuration.Accessor;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.docker.Engine;
import net.oneandone.stool.docker.Stats;
import net.oneandone.stool.templates.TemplateField;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.LogEntry;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.StandardProperty;
import net.oneandone.stool.util.TemplateProperty;
import net.oneandone.sushi.fs.DeleteException;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.NodeNotFoundException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.eclipse.aether.repository.RepositoryPolicy;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.oneandone.stool.stage.Project.State.UP;

/** represents the former backstage directory */
public class Stage {
    public final Session session;
    private final String id;
    public final FileNode directory;
    private final StageConfiguration configuration;
    private Maven lazyMaven;

    public Stage(Session session, String id, FileNode directory, StageConfiguration configuration) {
        this.session = session;
        this.id = id;
        this.directory = directory;
        this.configuration = configuration;
        this.lazyMaven = null;
    }

    public String getId() {
        return id;
    }

    public StageConfiguration config() {
        return configuration;
    }

    public String getName() {
        return config().name;
    }

    //-- pid file handling


    public String backstageLock() {
        return "backstage-" + id;
    }

    public String directoryLock() {
        return "directory-" + id;
    }

    public boolean isWorking() throws IOException {
        return session.lockManager.hasExclusiveLocks(directoryLock(), backstageLock());
    }

    public Project.State state() throws IOException {
        if (session.bedroom.contains(id)) {
            return Project.State.SLEEPING;
        } else if (dockerContainer() != null) {
            return UP;
        } else {
            return Project.State.DOWN;
        }

    }

    public void checkNotUp() throws IOException {
        if (state() == UP) {
            throw new IOException("stage is not stopped.");
        }
    }

    public String displayState() throws IOException {
        switch (isWorking() ? Project.State.WORKING : state()) {
            case UP:
                return "success";
            case WORKING:
                return "primary";
            default:
                return "danger";
        }
    }

    //--

    public List<Field> fields() throws IOException {
        List<Field> fields;

        fields = new ArrayList<>();
        fields.add(new Field("id") {
            @Override
            public Object get() {
                return id;
            }
        });
        fields.add(new Field("backstage") {
            @Override
            public Object get() {
                return directory.getAbsolute();
            }
        });
        fields.add(new Field("state") {
            @Override
            public Object get() throws IOException {
                return state().toString();
            }
        });
        fields.add(new Field("cpu") {
            @Override
            public Object get() throws IOException {
                String container;
                Engine engine;
                Stats stats;

                container = dockerContainer();
                if (container == null) {
                    return null;
                }
                engine = session.dockerEngine();
                stats = engine.containerStats(container);
                if (stats != null) {
                    return stats.cpu;
                } else {
                    // not started
                    return 0;
                }
            }
        });
        fields.add(new Field("mem") {
            @Override
            public Object get() throws IOException {
                String container;
                Engine engine;
                Stats stats;

                container = dockerContainer();
                if (container == null) {
                    return null;
                }
                engine = session.dockerEngine();
                stats = engine.containerStats(container);
                if (stats != null) {
                    return stats.memoryUsage * 100 / stats.memoryLimit;
                } else {
                    // not started
                    return 0;
                }
            }
        });
        fields.add(new Field("container") {
            @Override
            public Object get() throws IOException {
                return dockerContainer();
            }
        });
        return fields;
    }

    public List<Property> properties() {
        List<Property> result;
        Map<String, String> env;
        String prefix;

        result = new ArrayList<>();
        for (Accessor type : session.accessors().values()) {
            if (!type.name.equals("template.env")) {
                result.add(new StandardProperty(type, configuration));
            }
        }
        env = configuration.templateEnv;
        prefix = configuration.template.getName() + ".";
        for (String name : configuration.templateEnv.keySet()) {
            result.add(new TemplateProperty(prefix + name, env, name));
        }
        return result;
    }

    public Property propertyOpt(String name) {
        for (Property property : properties()) {
            if (name.equals(property.name())) {
                return property;
            }
        }
        return null;
    }

    //-- Maven stuff

    public void setMaven(Maven maven) {
        this.lazyMaven = maven;
    }

    /** CAUTION: this is not a session method, because it respects the stage repository */
    public Maven maven() throws IOException {
        World world;
        String mavenHome;
        FileNode settings;

        if (lazyMaven == null) {
            world = session.world;
            mavenHome = configuration.mavenHome();
            if (mavenHome == null) {
                settings = session.home.join("maven-settings.xml");
            } else {
                settings = world.file(mavenHome).join("conf/settings.xml");
            }
            // CAUTION: shared plexus - otherwise, Maven components are created over and over again
            lazyMaven = Maven.withSettings(world, localRepository(), settings, null, session.plexus(), null, null);
            // always get the latest snapshots
            lazyMaven.getRepositorySession().setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        }
        return lazyMaven;
    }


    public FileNode localRepository() {
        return session.configuration.shared ? directory.join(".m2") : session.world.getHome().join(".m2/repository");
    }

    public void cleanupMavenRepository(Console console) throws NodeNotFoundException, DeleteException {
        FileNode repository;

        repository = directory.join(".m2");
        if (repository.exists()) {
            console.info.println("Removing Maven Repository at " + repository.getAbsolute());
            repository.deleteTree();
        } else {
            console.verbose.println("No Maven Repository found at " + repository.getAbsolute());
        }
    }

    public Logs logs() {
        return new Logs(directory.join("logs"));
    }

    public void rotateLogs(Console console) throws IOException {
        Node archived;

        for (Node logfile : directory.find("**/*.log")) {
            archived = archiveDirectory(logfile).join(logfile.getName() + ".gz");
            console.verbose.println(String.format("rotating %s to %s", logfile.getRelative(directory), archived.getRelative(directory)));
            logfile.gzip(archived);
            logfile.deleteFile();
        }
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private Node archiveDirectory(Node node) throws MkdirException {
        return node.getParent().join("archive", FMT.format(LocalDateTime.now())).mkdirsOpt();
    }

    //--

    public FileNode dockerContainerFile() {
        return directory.join("container.id");
    }

    public String dockerContainer() throws IOException {
        FileNode file;

        file = dockerContainerFile();
        return file.exists() ? file.readString().trim() : null;
    }

}
