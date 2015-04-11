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
package net.oneandone.stool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import net.oneandone.stool.configuration.Bedroom;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.cli.Value;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Diff;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SystemImport extends SessionCommand {
    public static enum Include {
        ALL(true, true), CONFIG(true, false), STAGES(false, true);

        public final boolean withConfig;
        public final boolean withStages;

        private Include(boolean withConfig, boolean withStages) {
            this.withConfig = withConfig;
            this.withStages = withStages;
        }
    }

    public SystemImport(Session session) {
        this(session, null);
    }

    public SystemImport(Session session, FileNode oldHome) {
        super(session);
        this.oldHome = oldHome;
    }

    @Value(name = "oldHome", position = 1)
    private FileNode oldHome;

    @Option("include")
    private Include include = Include.ALL;

    private final List<FileNode> oldWrappers = new ArrayList<>();

    @Remaining
    public void select(String name) {
        FileNode wrapper;

        wrapper = oldHome.join("wrappers", name);
        if (!wrapper.isDirectory()) {
            throw new ArgumentException("old stage not found: " + wrapper.getAbsolute());
        }
        oldWrappers.add(wrapper);
    }

    @Override
    public void doInvoke() throws Exception {
        Bedroom oldBedroom;
        final Bedroom newBedroom;
        String newBedroomOrig;
        String name;
        List<Patch> patches;
        Iterator<Patch> iter;
        Patch patch;

        oldHome.checkDirectory();
        if (oldHome.equals(session.home)) {
            throw new ArgumentException("cannot import from myself");
        }
        console.info.println();
        console.info.println("CAUTION: import has the following limitations:");
        console.info.println("  * 'defaults' are not migrated");
        console.info.println("  * 'portPrefixes' are gone, your stage will run under a new port");
        console.info.println();
        oldBedroom = Bedroom.loadOrCreate(session.gson, oldHome);
        newBedroom = Bedroom.loadOrCreate(session.gson, session.home);
        newBedroomOrig = newBedroom.stages().toString();
        patches = new ArrayList<>();
        if (include.withConfig) {
            patches.add(stoolConfig());
        }
        if (include.withStages) {
            if (oldWrappers.isEmpty()) {
                for (FileNode oldWrapper : oldHome.join("wrappers").list()) {
                    name = oldWrapper.getName();
                    if (session.wrappers.join(name).exists()) {
                        console.info.println("ignoring stage that already exists: " + name);
                    } else {
                        oldWrappers.add(oldWrapper);
                    }
                }
            }
            for (FileNode oldWrapper : oldWrappers) {
                name = oldWrapper.getName();
                if (oldBedroom.stages().contains(name)) {
                    newBedroom.add(session.gson, name);
                }
                patches.add(stage(oldWrapper));
            }
        }
        patches.add(new Patch("M " + newBedroom.file(), Diff.diff(newBedroomOrig, newBedroom.stages().toString())) {
            @Override
            public void apply() throws IOException {
                newBedroom.save(session.gson);
            }
        });
        iter = patches.iterator();
        while (iter.hasNext()) {
            patch = iter.next();
            if (patch.message.isEmpty()) {
                iter.remove();
            } else {
                console.info.println(patch.header);
                console.info.println(Strings.indent(patch.message, "  "));
            }
        }
        if (patches.isEmpty()) {
            console.info.println("nothing to do.");
        } else {
            console.pressReturn();
            for (Patch p : patches) {
                p.apply();
            }
        }
    }

    private Patch stoolConfig() throws IOException {
        String current;
        final String path = "config.json";
        final FileNode dest;
        final String result;
        String diff;

        dest = session.home.join(path);
        current = dest.readString();
        result = mergeConfig(oldHome.join(path).readString(), current,
                new Object() {
                    void sharedBinRemove() {}
                    void versionRemove() {}
                    void stagesRemove() {}
                    // because values are all-strings no (e.g. in tomcat.select)
                    void defaultsRemove() {}

                    String portPrefixFirstRename() {
                        return "portFirst";
                    }
                    JsonElement portPrefixFirstTransform(JsonElement orig) {
                        return new JsonPrimitive(orig.getAsInt() * 10);
                    }

                    String portPrefixLastRename() {
                        return "portLast";
                    }
                    JsonElement portPrefixLastTransform(JsonElement orig) {
                        return new JsonPrimitive(orig.getAsInt() * 10 + 9);
                    }

                    String autoremoveRename() {
                        return "autoRemove";
                    }
                });
        diff = Diff.diff(current, result);
        return new Patch("M " + dest.getAbsolute(), diff) {
            public void apply() throws IOException {
                dest.writeString(result);
            }
        };
    }

    private Patch stage(FileNode oldWrapper) throws IOException {
        final FileNode tmpWrapper;
        final FileNode destWrapper;
        FileNode directory;
        final Stage stage;
        String url;
        String msg;
        FileNode tmpConfig;
        String tmp;

        directory = (FileNode) oldWrapper.join("anchor").resolveLink();
        directory.checkDirectory();
        url = Stage.probe(session.subversion(), directory);
        destWrapper = session.wrappers.join(oldWrapper.getName());
        destWrapper.checkNotExists();
        // Temp Wrapper in wrapper directory, because it fasted to move within the same filesystem.
        // And Sushi has problems to move the anchor symlink across file systems
        tmpWrapper = session.wrappers.createTempDirectory();
        Files.stoolDirectory(tmpWrapper);
        stage = Stage.createOpt(session, url, session.createStageConfiguration(url), tmpWrapper, directory);
        stage.tuneConfiguration();
        stage.saveWrapper();
        stage.getDirectory().link(stage.anchor());
        tmpConfig = tmpWrapper.join("config.json");
        tmp = tmpConfig.readString();
        tmp = mergeConfig(oldWrapper.join("config.json").readString(), tmp,
                // 2.13 -> 3.0 changes
                new Object() {
                    void portPrefixRemove() {}
                });
        tmpConfig.writeString(tmp);
        msg = Diff.diff(oldWrapper.join("config.json").readString(), tmp);
        if (msg.isEmpty()) {
            // make sure the message is not empty, because we have to move the file
            msg = "(no config changes)";
        }
        return new Patch("A " + destWrapper.getAbsolute(), msg) {
            @Override
            public void apply() throws IOException {
                tmpWrapper.move(destWrapper);
                if (session.configuration.shared) {
                    // TODO: half of the command is a noop because the stage directory is already owned
                    session.chown(stage, stage.owner());
                }
            }
        };
    }

    public static String mergeConfig(String srcString, String destString, Object mapper) throws IOException {
        JsonParser parser;
        JsonObject src;
        JsonObject dest;
        Object[] mapped;

        parser = new JsonParser();
        src = (JsonObject) parser.parse(srcString);
        dest = (JsonObject) parser.parse(destString);
        for (Map.Entry<String, JsonElement> entry : src.entrySet()) {
            mapped = map(mapper, entry.getKey(), entry.getValue());
            if (mapped != null) {
                dest.add((String) mapped[0], (JsonElement) mapped[1]);
            }
        }
        return toString(dest);
    }

    private static Object[] map(Object mapper, String name, JsonElement value) {
        Class clazz;
        Method rename;
        Method transform;

        clazz = mapper.getClass();
        if (method(clazz, name + "Remove") != null) {
            return null;
        }
        rename = method(clazz, name + "Rename");
        transform = method(clazz, name + "Transform", JsonElement.class);
        return new Object[] { rename(rename, mapper, name), transform(transform, mapper, value) };
    }

    /* Search with method by name, check arguments later. Helps to detect methods with wrong arguments. */
    private static Method method(Class clazz, String name, Class ... args) {
        Method result;

        result = null;
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                if (result != null) {
                    throw new ArgumentException("method ambiguous: " + name);
                }
                result = m;
            }
        }
        if (result != null) {
            if (!Arrays.equals(args, result.getParameterTypes())) {
                throw new ArgumentException("argument type mismatch for method " + name);
            }
        }
        return result;
    }

    private static String rename(Method method, Object object, String old) {
        if (method == null) {
            return old;
        } else {
            try {
                return (String) method.invoke(object);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        }
    }
    private static JsonElement transform(Method method, Object object, JsonElement old) {
        if (method == null) {
            return old;
        } else {
            try {
                return (JsonElement) method.invoke(object, old);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static String toString(JsonObject obj) {
        try {
            StringWriter stringWriter = new StringWriter();
            JsonWriter jsonWriter = new JsonWriter(stringWriter);
            jsonWriter.setIndent("  ");
            jsonWriter.setLenient(true);
            Streams.write(obj, jsonWriter);
            return stringWriter.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public abstract static class Patch {
        public final String header;
        public final String message;

        public Patch(String header, String message) {
            this.header = header;
            this.message = message;
        }

        public abstract void apply() throws IOException;
    }
}
