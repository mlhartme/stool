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
package net.oneandone.stool.setup;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.oneandone.inline.Console;
import net.oneandone.stool.cli.Import;
import net.oneandone.stool.cli.Main;
import net.oneandone.stool.configuration.Bedroom;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Diff;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpgradeBuilder {
    private final Console console;
    private final Home home;
    private final FileNode from;

    // assigned during upgrade:
    private StoolConfiguration stoolRaw;
    private Session session = null;
    private String currentStage = null;

    /** maps names to ids */
    private Map<String, String> stages = new HashMap<>();

    public UpgradeBuilder(Console console, Home home, FileNode from) {
        this.console = console;
        this.home = home;
        this.from = from;
    }

    public String version() throws IOException {
        return from.join("version").readString().trim();
    }

    public void run() throws IOException {
        String oldVersion;
        String newVersion;
        Upgrade stage33_34;

        newVersion = Main.versionString(from.getWorld());
        oldVersion = version();
        if (!oldVersion.startsWith("3.3.")) {
            throw new IOException("don't know how to upgrade " + oldVersion + " -> " + newVersion);
        }
        console.info.println("upgrade " + oldVersion + " -> " + newVersion);
        stage33_34 = stage33_34();
        all(stool33_34(stage33_34), stage33_34);
    }


    private void all(Upgrade stoolMapper, Upgrade stageMapper) throws IOException {
        String id;
        FileNode stage;
        Logging logging;
        Import i;

        stool(from, stoolMapper);
        logging = Logging.forHome(home.dir);
        session = Session.load(false, home.dir, logging, "upgrade", console, home.dir.getWorld(), null, null);
        for (FileNode oldBackstage : from.join("backstages").list()) {
            id = getId(oldBackstage.join("config.json"));
            currentStage = oldBackstage.getName();
            if (home.dir.join("backstages", id).isDirectory()) {
                console.info.println("stage already imported, ignored: " + currentStage + " (" + id + ")");
                continue;
            }
            console.info.println("import " + oldBackstage);
            stages.put(currentStage, id);
            stage = oldBackstage.join("anchor").resolveLink();
            i = new Import(session);
            i.setUpgradeId(id);
            i.dirs(stage.getAbsolute());
            i.doRun();
            transform(oldBackstage.join("config.json"), stage.join(".backstage/config.json"), stageMapper);
        }
        ports();
        bedroom();
    }

    private void ports() throws IOException {
        List<String> entries;
        List<String> out;
        String name;
        String id;

        out = new ArrayList<>();
        for (String line : from.join("run/ports").readLines()) {
            entries = Separator.SPACE.split(line);
            name = entries.get(2);
            id = stages.get(name);
            if (id == null) {
                throw new IllegalStateException(name);
            }
            entries.add(3, id);
            out.add(Separator.SPACE.join(entries));
        }
        home.dir.join("run/ports").writeLines(out);
    }

    private void bedroom() throws IOException {
        Gson gson;
        Bedroom old;
        Bedroom next;
        String id;

        gson = home.gson();
        old = Bedroom.loadOrCreate(gson, from);
        next = Bedroom.loadOrCreate(gson, home.dir);
        for (String name : old) {
            id = stages.get(name);
            if (id == null) {
                throw new IllegalStateException(name);
            }
            next.add(gson, id);
        }
    }

    private static String getId(FileNode config) throws IOException {
        JsonObject json;

        try (Reader src = config.newReader()) {
            json = new JsonParser().parse(src).getAsJsonObject();
            return json.get("id").getAsString();
        }
    }

    private void stool(FileNode from, Upgrade stoolMapper) throws IOException {
        String in;
        String without;
        String preliminary;

        in = from.join("config.json").readString();
        without = Transform.transform(in, new Upgrade() {
            void defaultsRemove() {}
        });
        preliminary = Transform.transform(without, stoolMapper);
        stoolRaw = home.gson().fromJson(preliminary, StoolConfiguration.class);
        transform(in, home.dir.join("config.json"), stoolMapper);
    }

    private void transform(FileNode src, FileNode dest, Upgrade mapper) throws IOException {
        transform(src.readString(), dest, mapper);
    }

    private void transform(String in, FileNode dest, Upgrade mapper) throws IOException {
        String out;

        console.verbose.println("upgrade " + dest.getAbsolute());
        out = Transform.transform(in, mapper);
        if (!in.equals(out)) {
            console.info.println("M " + dest.getAbsolute());
            console.info.println(Strings.indent(Diff.diff(in, out), "  "));
            dest.writeString(out);
        }
    }

    //--

    public Upgrade stool33_34(Upgrade stage33_34) {
        return new Upgrade() {
            void promptRemove() {
            }

            JsonElement defaultsTransform(JsonElement element) {
                JsonObject result;
                JsonObject obj;
                String url;
                JsonObject defaults;

                result = new JsonObject();
                obj = element.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    url = entry.getKey();
                    defaults = entry.getValue().getAsJsonObject();
                    result.add(url.isEmpty() ? "" : "svn:" + url, migrate(defaults));
                }
                return result;
            }

            String contactAdminRename() {
                return "admin";
            }

            //--

            private JsonObject migrate(JsonObject e) {
                String str;

                str = Transform.transform(Transform.toString(e), stage33_34);
                return new JsonParser().parse(str).getAsJsonObject();
            }
        };
    }

    public Upgrade stage33_34() {
        return new Upgrade() {
            JsonElement extensionsTransform(JsonElement e) {
                if (session == null) {
                    // in defaults -- handled in globals below
                    throw new IllegalStateException();
                }
                // in stage
                e.getAsJsonObject().remove("-pustefix.editor");
                e.getAsJsonObject().remove("+pustefix.editor");
                return e;
            }
            void idRemove() {
            }
            void sslUrlRemove() {
            }
            void tomcatPermRemove() {
            }
            String suffixesRename() {
                return "url";
            }
            JsonElement suffixesTransform(JsonElement e) {
                String hostpath;
                JsonArray array;

                hostpath = "%h:%p";
                if (session == null) {
                    // upgrade defaults - contains a string, not an array
                    array = new JsonArray();
                    for (String str : Separator.COMMA.split(e.getAsString())) {
                        array.add(str);
                    }
                } else {
                    array = e.getAsJsonArray();
                }
                if (stoolRaw.vhosts) {
                    hostpath = "%a.%s." + hostpath;
                }
                hostpath = hostpath + allSuffixes(array);
                return new JsonPrimitive("(http|https)://" + hostpath );
            }
            String untilRename() {
                return "expire";
            }
            void global(JsonObject src, JsonObject dest) {
                JsonArray array;

                if (session == null) {
                    // this call is to upgrade defaults
                    dest.remove("pustefix.editor.version");
                    dest.remove("pustefix.editor.userdata");
                } else {
                    array = new JsonArray();
                    array.add(new JsonPrimitive(StageConfiguration.NOTIFY_OWNER));
                    dest.add("notify", array);
                    dest.add("quota", new JsonPrimitive(10000));
                    dest.add("name", new JsonPrimitive(currentStage));
                }
            }
        };
    }

    public static String allSuffixes(JsonArray array) {
        int count;
        String str;
        StringBuilder builder;

        builder = new StringBuilder();
        count = 0;
        for (JsonElement e : array) {
            str = e.getAsString();
            if (str.contains("[]")) {
                throw new UnsupportedOperationException("don't know how to upgrade suffixes '" + str + "'");
            }
            if (count > 0) {
                builder.append('|');
            }
            builder.append(str);
            count++;
        }
        if (count == 1) {
            return builder.toString();
        } else {
            return "(" + builder.toString() + ")";
        }
    }
}
