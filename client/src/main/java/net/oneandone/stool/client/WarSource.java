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

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.docker.BuildArgument;
import net.oneandone.stool.docker.BuildError;
import net.oneandone.stool.docker.Daemon;
import net.oneandone.stool.docker.ImageInfo;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

    public String subst(String name) throws IOException {
        return name.replace(SUBST, app());
    }

    public String app() throws IOException {
        String result;

        result = properties().get(APP_ARGUMENT);
        return result == null ? "app": result;
    }

    public Map<String, String> properties() throws IOException {
        Node<?> node;
        Properties all;
        Map<String, String> result;

        node = war.openZip().join(PROPERTIES_FILE);
        result = new HashMap<>();
        if (node.exists()) {
            all = node.readProperties();
            for (String property : all.stringPropertyNames()) {
                if (property.startsWith(PROPERTIES_PREFIX)) {
                    result.put(property.substring(PROPERTIES_PREFIX.length()), all.getProperty(property));
                }
            }
        }
        return result;
    }

    public Map<String, String> arguments(Map<String, String> explicit) throws IOException {
        Map<String, String> result;

        result = properties();
        result.putAll(explicit);
        result.remove(APP_ARGUMENT);
        return result;
    }

    //--

    private static String originUser() {
        try {
            return System.getProperty("user.name") + '@' + InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            return "unknown host: " + e.getMessage();
        }
    }

    //--

    public String build(Globals globals, String comment, int keep, boolean noCache,
                        Daemon engine, Reference reference, String originScm, Map<String, String> explicitArguments)
            throws Exception {
        Console console;
        long started;
        String registryPrefix;
        Map<String, String> arguments;
        int tag;
        String image;
        String repositoryTag;
        FileNode context;
        Map<String, String> labels;
        FileNode template;
        Map<String, BuildArgument> defaults;
        Map<String, String> buildArgs;
        StringWriter output;
        String str;

        started = System.currentTimeMillis();
        console = globals.getConsole();
        console.info.println("building image for " + war + " (" + (war.size() / (1024 * 1024)) + " mb)");
        registryPrefix = globals.configuration().registryPrefix() + reference.client.getContext() + "/";
        arguments = arguments(explicitArguments);
        template = template(globals, arguments);
        tag = wipeOldImages(console, engine, registryPrefix, reference.stage, keep);

        repositoryTag = registryPrefix + reference.stage + ":" + tag;
        defaults = BuildArgument.scan(template.join("Dockerfile"));
        buildArgs = buildArgs(defaults, arguments);
        context = createContext(template);

        labels = new HashMap<>();
        labels.put(ImageInfo.IMAGE_LABEL_COMMENT, comment);
        labels.put(ImageInfo.IMAGE_LABEL_ORIGIN_SCM, originScm);
        labels.put(ImageInfo.IMAGE_LABEL_ORIGIN_USER, originUser());
        for (Map.Entry<String, String> arg : buildArgs.entrySet()) {
            labels.put(ImageInfo.IMAGE_LABEL_ARG_PREFIX + arg.getKey(), arg.getValue());
        }
        console.verbose.println("building context " + context.getAbsolute());
        output = new StringWriter();
        try {
            image = engine.imageBuild(repositoryTag, buildArgs, labels, context, noCache, output);
        } catch (BuildError e) {
            console.info.println("build failed: " + e.error);
            console.info.println("build output:");
            console.info.println(e.output);
            throw new ArgumentException("build failed");
        } finally {
            output.close();
        }
        str = output.toString();
        console.verbose.println("successfully built image: " + image);
        console.verbose.println(str);
        console.verbose.println("pushing ...");
        console.info.println(engine.imagePush(repositoryTag));
        console.verbose.println("done");
        console.info.println("done: image " + tag + " (" + (System.currentTimeMillis() - started) / 1000 + " seconds)");
        return repositoryTag;
    }


    /** @return next version */
    public int wipeOldImages(Console console, Daemon docker, String registryPrefix, String name, int keep) throws IOException {
        Map<String, ImageInfo> images;

        int count;
        int result;
        List<String> sorted;
        String remove;

        images = repositoryTags(registryPrefix, name, docker.imageList());
        result = nextTag(images.keySet());
        sorted = new ArrayList<>(images.keySet());
        Collections.sort(sorted);

        count = sorted.size() - keep;
        while (count > 0 && !sorted.isEmpty()) {
            remove = sorted.remove(0);
            if (!hasContainer(docker, remove)) {
                console.info.println("remove image: " + remove);
                docker.imageRemove(remove, false);
                count--;
            } else {
                console.verbose.println("cannot remove image, because it's still in use: " + remove);
            }
        }
        return result;
    }

    private boolean hasContainer(Daemon engine, String repoTag) throws IOException {
        return engine.containerListForImage(repoTag).size() > 0;
    }

    public static String tag(String repositoryTag) {
        String result;
        int idx;

        result = repositoryTag;
        idx = result.lastIndexOf(':');
        if (idx == -1) {
            throw new IllegalArgumentException(result);
        }
        return result.substring(idx + 1);
    }

    public static int nextTag(Collection<String> repositoryTags) {
        String tag;
        int number;
        int max;

        max = 0;
        for (String repoTag : repositoryTags) {
            tag = tag(repoTag);
            try {
                number = Integer.parseInt(tag);
                if (number > max) {
                    max = number;
                }
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return max + 1;
    }

    public Map<String, ImageInfo> repositoryTags(String registryPrefix, String name, Map<String, ImageInfo> imageMap) {
        Map<String, ImageInfo> result;
        ImageInfo info;

        result = new HashMap<>();
        for (Map.Entry<String, ImageInfo> entry : imageMap.entrySet()) {
            info = entry.getValue();
            for (String repositoryTag : info.repositoryTags) {
                if (repositoryTag.startsWith(registryPrefix + name + ":")) {
                    result.put(repositoryTag, info);
                }
            }
        }
        return result;
    }


    private FileNode createContext(FileNode src) throws IOException {
        FileNode context;
        FileNode destparent;
        FileNode destfile;

        context = war.getWorld().getTemp().createTempDirectory();
        war.copyFile(context.join("app.war"));
        for (FileNode srcfile : src.find("**/*")) {
            if (srcfile.isDirectory()) {
                continue;
            }
            destfile = context.join(srcfile.getRelative(src));
            destparent = destfile.getParent();
            destparent.mkdirsOpt();
            srcfile.copy(destfile);
        }
        return context;
    }

    private FileNode template(Globals globals, Map<String, String> arguments) throws IOException {
        return globals.templates().join(eat(arguments, "_template", "war")).checkDirectory();
    }

    private String eat(Map<String, String> arguments, String key, String dflt) {
        String explicitValue;

        explicitValue = arguments.remove(key);
        return explicitValue != null ? explicitValue : dflt;
    }

    private Map<String, String> buildArgs(Map<String, BuildArgument> defaults, Map<String, String> arguments) {
        Map<String, String> result;
        String property;

        result = new HashMap<>();
        for (BuildArgument arg : defaults.values()) {
            result.put(arg.name, arg.dflt);
        }
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            property = entry.getKey();
            if (!result.containsKey(property)) {
                throw new ArgumentException("unknown explicit build argument: " + property + "\n" + available(defaults.values()));
            }
            result.put(property, entry.getValue());
        }
        return result;
    }

    private static String available(Collection<BuildArgument> args) {
        StringBuilder result;

        result = new StringBuilder();
        result.append("(available build arguments:");
        for (BuildArgument arg : args) {
            result.append(' ');
            result.append(arg.name);
        }
        result.append(")\n");
        return result.toString();
    }
}
