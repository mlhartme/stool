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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.oneandone.inline.Console;
import net.oneandone.stool.cli.Main;
import net.oneandone.stool.cli.Import;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Diff;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.Map;

public class UpgradeBuilder {
    private final Console console;
    private final Home home;
    private final FileNode from;

    // assigned during upgrade:
    private Session session = null;
    private String currentStage = null;

    public UpgradeBuilder(Console console, Home home, FileNode from) {
        this.console = console;
        this.home = home;
        this.from = from;
    }

    public void run() throws IOException {
        String oldVersion;
        String newVersion;
        Upgrade stage33_34;

        newVersion = Main.versionString(from.getWorld());
        oldVersion = from.join("version").readString().trim();
        if (!oldVersion.startsWith("3.3.")) {
            throw new IOException("don't know how to upgrade " + oldVersion + " -> " + newVersion);
        }
        console.info.println("upgrade " + oldVersion + " -> " + newVersion);
        stage33_34 = stage33_34();
        doUpgrade(stool33_34(stage33_34), stage33_34);
    }


    private void doUpgrade(Upgrade stoolMapper, Upgrade stageMapper) throws IOException {
        FileNode stage;
        String user;
        Logging logging;
        Import i;

        home.create();
        doUpgradeStool(from, stoolMapper);
        user = System.getProperty("user.name");
        logging = Logging.forHome(home.dir, user);
        session = Session.load(false, home.dir, logging, user, "upgrade", console, home.dir.getWorld(), null, null);
        for (FileNode oldBackstage : from.join("backstages").list()) {
            console.info.println("upgrade " + oldBackstage);
            currentStage = oldBackstage.getName();
            stage = oldBackstage.join("anchor").resolveLink();
            i = new Import(session);
            i.dirs(stage.getAbsolute());
            i.doRun();
            transform(oldBackstage.join("config.json"), stage.join(".backstage/config.json"), stageMapper);
        }
    }

    private void doUpgradeStool(FileNode from, Upgrade stoolMapper) throws IOException {
        transform(from.join("config.json"), home.dir.join("config.json"), stoolMapper);
    }

    private void transform(FileNode src, FileNode dest, Upgrade mapper) throws IOException {
        String in;
        String out;

        console.verbose.println("upgrade " + src.getAbsolute());
        in = src.readString();
        out = Transform.transform(in, mapper);
        if (!in.equals(out)) {
            console.info.println("M " + src.getAbsolute());
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
                    result.add("svn:" + url, migrate(defaults));
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
                e.getAsJsonObject().remove("-pustefix.editor");
                e.getAsJsonObject().remove("+pustefix.editor");
                return e;
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
                    return;
                }
                array = new JsonArray();
                array.add(new JsonPrimitive(StageConfiguration.NOTIFY_OWNER));
                dest.add("notify", array);
                dest.add("quota", new JsonPrimitive(10000));
                dest.add("name", new JsonPrimitive(currentStage));
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
