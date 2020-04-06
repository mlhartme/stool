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
package net.oneandone.stool.client.cli;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.client.App;
import net.oneandone.stool.client.docker.BuildArgument;
import net.oneandone.stool.client.docker.BuildError;
import net.oneandone.stool.client.docker.Engine;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.docker.ImageInfo;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Build extends ProjectCommand {
    private final boolean noCache;
    private final int keep;
    private final boolean restart;
    private final String comment;
    private final Map<String, String> explicitArguments;

    public Build(Globals globals, boolean noCache, int keep, boolean restart, String comment, List<String> args) {
        super(globals);
        this.noCache = noCache;
        this.keep = keep;
        this.restart = restart;
        this.comment = comment;
        this.explicitArguments = argument(args);
    }

    private static Map<String, String> argument(List<String> args) {
        int idx;
        Map<String, String> result;

        result = new HashMap<>();
        for (String arg : args) {
            idx = arg.indexOf('=');
            if (idx == -1) {
                throw new ArgumentException("invalid argument: <key>=<value> expected, got " + arg);
            }
            result.put(arg.substring(0, idx), arg.substring(idx + 1));
        }
        return result;
    }

    @Override
    public void doRun(FileNode directory) throws Exception {
        Project project;
        List<App> apps;
        FileNode war;

        project = Project.lookup(directory);
        if (project == null) {
            throw new ArgumentException("unknown project");
        }
        apps = project.list(globals.servers());
        if (apps.isEmpty()) {
            throw new IOException("no apps to build in project " + directory);
        }
        try (Engine engine = Engine.create()) {
            for (App app : apps) {
                war = directory.findOne(app.path);
                build(engine, app.reference.stage, war, project.getOriginOrUnknown(), createdOn(), app.arguments(war, explicitArguments));
                if (restart) {
                    new Restart(globals, null).doRun(app.reference);
                }
            }
        }
    }

    private static String createdOn() {
        try {
            return System.getProperty("user.name") + '@' + InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            return "unknown host: " + e.getMessage();
        }
    }

    //--

    private static final String REGISTRY_NAMESPACE = "contargo.server.lan/mhm"; // TODO

    public void build(Engine engine, String stage, FileNode war, String originScm, String originUser, Map<String, String> arguments)
            throws Exception {
        long started;
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
        console.info.println("building image for " + war + " (" + (war.size() / (1024 * 1024)) + " mb)");
        template = template(arguments);
        tag = wipeOldImages(engine, stage);
        context = createContext(stage, war);  // this is where concurrent builds are blocked
        try {
            repositoryTag = REGISTRY_NAMESPACE + "/" + stage + ":" + tag;
            defaults = BuildArgument.scan(template.join("Dockerfile"));
            buildArgs = buildArgs(defaults, arguments);
            populateContext(context, template);
            labels = new HashMap<>();
            labels.put(ImageInfo.IMAGE_LABEL_COMMENT, comment);
            labels.put(ImageInfo.IMAGE_LABEL_ORIGIN_SCM, originScm);
            labels.put(ImageInfo.IMAGE_LABEL_ORIGIN_USER, originUser);
            labels.put(ImageInfo.IMAGE_LABEL_CREATED_BY, "TODO");
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
            console.info.println("done: image " + tag + " (" + (System.currentTimeMillis() - started) / 1000 + " seconds)");
        } finally {
            cleanupContext(Integer.toString(tag), keep);
        }
    }


    /** @return next version */
    public int wipeOldImages(Engine engine, String name) throws IOException {
        Map<String, ImageInfo> images;

        int count;
        int result;

        images = repositoryTags(name, engine.imageList());
        return nextTag(images.keySet());

        /* TODO
        result = nextTag(images.keySet());
        count = images.size() - keep;
        while (count > 0 && !images.isEmpty()) {
            remove = images.remove(0);
            if (!hasContainer(engine, remove)) {
                Server.LOGGER.debug("remove image: " + remove);
                engine.imageRemove(remove.repositoryTag, false);
                count--;
            } else {
                Server.LOGGER.debug("cannot remove image, because it's still in use: " + remove);
            }
        }
        return result; */
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

    public Map<String, ImageInfo> repositoryTags(String name, Map<String, ImageInfo> imageMap) {
        Map<String, ImageInfo> result;
        ImageInfo info;

        result = new HashMap<>();
        for (Map.Entry<String, ImageInfo> entry : imageMap.entrySet()) {
            info = entry.getValue();
            for (String repositoryTag : info.repositoryTags) {
                if (repositoryTag.startsWith(REGISTRY_NAMESPACE + "/" + name + ":")) {
                    result.put(repositoryTag, info);
                }
            }
        }
        return result;
    }


    private void populateContext(FileNode context, FileNode src) throws IOException {
        FileNode destparent;
        FileNode destfile;

        for (FileNode srcfile : src.find("**/*")) {
            if (srcfile.isDirectory()) {
                continue;
            }
            destfile = context.join(srcfile.getRelative(src));
            destparent = destfile.getParent();
            destparent.mkdirsOpt();
            srcfile.copy(destfile);
        }
    }

    private FileNode template(Map<String, String> arguments) throws IOException {
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

    private JsonObject buildResult(String image, String error, String output) {
        JsonObject result;

        result = new JsonObject();
        result.add("tag", new JsonPrimitive(image));
        if (error != null) {
            result.add("error", new JsonPrimitive(error));
        }
        result.add("output", new JsonPrimitive(output));
        return result;
    }

    //--

    public FileNode createContext(String name, FileNode war) throws IOException {
        FileNode result;

        /* TODO
        result = globals.contexts().join("context").mkdirOpt().join("_");
        try {
            result.mkdir();
        } catch (MkdirException e) {
            throw new ArgumentException("another build for stage " + name + " is in progress, try again later");
        }
        */
        result = war.getWorld().getTemp().createTempDirectory(); // TODO
        war.copyFile(result.join("app.war"));
        return result;
    }

    public void cleanupContext(String tag, int keepNo) throws IOException {
        FileNode dir;
        List<FileNode> lst;
        FileNode dest;

        /* TODO
        dir = globals.contexts().join("context");
        dest = dir.join(tag);
        moveAway(dest);
        dir.join("_").move(dest);
        lst = dir.list();
        Collections.sort(lst, new Comparator<FileNode>() {
            @Override
            public int compare(FileNode left, FileNode right) {
                try {
                    return (int) (left.getLastModified() - right.getLastModified());
                } catch (GetLastModifiedException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        while (lst.size() > keep) {
            lst.remove(0).deleteTree();
        }
         */
    }

    private void moveAway(FileNode file) throws IOException {
        int no;
        FileNode away;

        if (file.exists()) {
            for (no = 1; true; no++) {
                away = file.getParent().join(file.getName() + "_" + no);
                if (!away.exists()) {
                    file.move(away);
                    return;
                }
            }
        }
    }
}
