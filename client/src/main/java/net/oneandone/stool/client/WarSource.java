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
package net.oneandone.stool.client;

import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.SizeException;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** List of Apps. Represents .backstage */
public class WarSource extends Source {
    /* TODO: configurable
    public static final String PROPERTIES_FILE = "WEB-INF/classes/META-INF/stool.properties";
    public static final String PROPERTIES_PREFIX = ""; */
    public static final String PROPERTIES_FILE = "WEB-INF/classes/META-INF/pominfo.properties";
    public static final String PROPERTIES_PREFIX = "stool.";

    public static final String APP_ARGUMENT = "_app";

    public static List<WarSource> find(FileNode directory) throws IOException {
        List<WarSource> result;

        result = new ArrayList<>();
        doFind(directory, result);
        return result;
    }

    private static void doFind(FileNode directory, List<WarSource> result) throws IOException {
        WarSource war;

        war = createOpt(directory);
        if (war != null) {
            result.add(war);
        } else {
            for (FileNode child : directory.list()) {
                if (child.isDirectory()) {
                    doFind(child, result);
                }
            }
        }
    }

    public static WarSource createOpt(FileNode directory) throws IOException {
        List<FileNode> lst;

        if (!directory.join("pom.xml").isFile()) {
            return null;
        }
        lst = directory.find("target/*.war");
        switch (lst.size()) {
            case 0:
                return null;
            case 1:
                return new WarSource(directory, lst.get(0));
            default:
                throw new IOException("ambiguous: " + directory + " " + lst);
        }
    }

    //--

    public final FileNode war;

    public WarSource(FileNode directory, FileNode war) {
        super(Type.WAR, directory);
        this.war = war;
    }

    public String app() throws IOException {
        properties();
        return lazyApp;
    }

    public Map<String, String> implicitArguments() throws IOException {
        return new HashMap<>(properties());
    }

    public FileNode createContext(Globals globals, Map<String, String> arguments) throws IOException {
        FileNode template;
        FileNode context;
        FileNode destparent;
        FileNode destfile;

        template = globals.templates().join(eat(arguments, "_template", "vanilla-war")).checkDirectory();
        context = war.getWorld().getTemp().createTempDirectory();
        war.copyFile(context.join("app.war"));
        for (FileNode srcfile : template.find("**/*")) {
            if (srcfile.isDirectory()) {
                continue;
            }
            destfile = context.join(srcfile.getRelative(template));
            destparent = destfile.getParent();
            destparent.mkdirsOpt();
            srcfile.copy(destfile);
        }
        return context;
    }

    public String toString() {
        try {
            return "war " + war + " (" + (war.size() / (1024 * 1024)) + " mb)";
        } catch (SizeException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    //--

    private Map<String, String> lazyProperties = null;
    private String lazyApp = null;
    private Map<String, String> properties() throws IOException {
        Node<?> node;
        Properties all;

        if (lazyProperties == null) {
            node = war.openZip().join(PROPERTIES_FILE);
            lazyProperties = new HashMap<>();
            if (node.exists()) {
                all = node.readProperties();
                for (String property : all.stringPropertyNames()) {
                    if (property.startsWith(PROPERTIES_PREFIX)) {
                        lazyProperties.put(property.substring(PROPERTIES_PREFIX.length()), all.getProperty(property));
                    }
                }
            }
            lazyApp = lazyProperties.remove(APP_ARGUMENT);
            if (lazyApp == null) {
                lazyApp = "app";
            }
        }
        return lazyProperties;
    }
}
