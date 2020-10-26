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
package net.oneandone.stool.stockimage;

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.docker.BuildArgument;
import net.oneandone.stool.docker.BuildError;
import net.oneandone.stool.docker.Daemon;
import net.oneandone.stool.docker.ImageInfo;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;

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

/** List of Apps. Represents .backstage */
public abstract class Source {
    public enum Type {
        WAR, DOCKER
    }

    public static final String SUBST = "_";

    public static boolean hasSubst(String name) {
        return name.contains(SUBST);
    }

    //--

    public static List<? extends Source> findAndCheck(Type type, FileNode directory, String stage) throws IOException {
        List<? extends Source> sources;

        directory.checkDirectory();
        switch (type) {
            case WAR:
                sources = WarSource.find(directory);
                break;
            case DOCKER:
                sources = DockerSource.find(directory);
                break;
            default:
                throw new IllegalStateException();
        }
        if (sources.isEmpty()) {
            throw new ArgumentException(directory.getAbsolute() + ": no wars found - did you build your project?");
        } else if (sources.size() > 1) {
            if (!stage.contains(SUBST)) {
                throw new ArgumentException(stage + ": missing '" + SUBST + "' in stage name to attach " + sources.size() + " stages");
            }
        }
        return sources;
    }

    //--

    public final Type type;
    public final FileNode directory;

    public Source(Type type, FileNode directory) {
        this.type = type;
        this.directory = directory;
    }

    public String subst(String name) throws IOException {
        return name.replace(SUBST, app());
    }

    public String getOriginOrUnknown() throws IOException {
        FileNode dir;

        dir = directory;
        do {
            if (dir.join(".git").isDirectory()) {
                return "git:" + git(dir, "config", "--get", "remote.origin.url").exec().trim();
            }
            dir = dir.getParent();
        } while (dir != null);
        return "unknown";
    }

    private static Launcher git(FileNode cwd, String... args) {
        Launcher launcher;

        launcher = new Launcher(cwd, "git");
        launcher.arg(args);
        return launcher;
    }

    //--


    /** @return name of the app */
    public abstract String app() throws IOException;

    public abstract Map<String, String> implicitArguments() throws IOException;

    public abstract FileNode createContext(Globals globals, Map<String, String> arguments) throws IOException;

    //--

    public String build(Globals globals, Daemon daemon, Reference reference,
                        String comment, int keep, boolean noCache, Map<String, String> explicitArguments)
            throws Exception {
        Console console;
        long started;
        String registryPrefix;
        int tag;
        String repositoryTag;

        started = System.currentTimeMillis();
        console = globals.getConsole();
        console.info.println("building image for " + toString());
        registryPrefix = globals.configuration().registryPrefix() + reference.client.getContext() + "/";
        tag = wipeOldImages(console, daemon, registryPrefix, reference.stage, keep);
        repositoryTag = registryPrefix + reference.stage + ":" + tag;

        doBuild(globals, daemon, repositoryTag, comment, noCache, getOriginOrUnknown(), explicitArguments);

        console.verbose.println("pushing ...");
        console.info.println(daemon.imagePush(repositoryTag));
        console.verbose.println("done");
        console.info.println("done: image " + tag + " (" + (System.currentTimeMillis() - started) / 1000 + " seconds)");
        return repositoryTag;
    }

    private void doBuild(Globals globals, Daemon engine, String repositoryTag,
                         String comment, boolean noCache, String originScm, Map<String, String> explicitArguments) throws IOException {
        Console console;
        String str;
        Map<String, String> arguments;
        Map<String, String> buildArgs;
        FileNode context;
        StringWriter output;
        String image;

        arguments = implicitArguments();
        arguments.putAll(explicitArguments);
        context = createContext(globals, arguments);
        buildArgs = buildArgs(BuildArgument.scan(context.join("Dockerfile")), arguments);
        output = new StringWriter();
        console = globals.getConsole();
        try {
            image = engine.imageBuild(repositoryTag, buildArgs,
                    getLabels(comment, originScm, buildArgs), context, noCache, output);
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
    }

    private Map<String, String> getLabels(String comment, String originScm, Map<String, String> buildArgs) {
        Map<String, String> labels;

        labels = new HashMap<>();
        labels.put(ImageInfo.IMAGE_LABEL_COMMENT, comment);
        labels.put(ImageInfo.IMAGE_LABEL_ORIGIN_SCM, originScm);
        labels.put(ImageInfo.IMAGE_LABEL_ORIGIN_USER, originUser());
        for (Map.Entry<String, String> arg : buildArgs.entrySet()) {
            labels.put(ImageInfo.IMAGE_LABEL_ARG_PREFIX + arg.getKey(), arg.getValue());
        }
        return labels;
    }

    private static String originUser() {
        try {
            return System.getProperty("user.name") + '@' + InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            return "unknown host: " + e.getMessage();
        }
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

    protected String eat(Map<String, String> arguments, String key, String dflt) {
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
