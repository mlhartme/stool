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

import net.oneandone.stool.configuration.Bedroom;
import net.oneandone.stool.configuration.Property;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.setup.Transform;
import net.oneandone.stool.setup.Upgrade;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SystemImport extends SessionCommand {
    public enum Include {
        ALL(true, true), CONFIG(true, false), STAGES(false, true);

        public final boolean withConfig;
        public final boolean withStages;

        Include(boolean withConfig, boolean withStages) {
            this.withConfig = withConfig;
            this.withStages = withStages;
        }
    }

    public SystemImport(Session session) {
        this(session, null);
    }

    public SystemImport(Session session, FileNode oldLib) {
        super(session, Mode.EXCLUSIVE);
        this.oldLib = oldLib;
    }

    @Value(name = "oldLib", position = 1)
    private FileNode oldLib;

    @Option("include")
    private Include include = Include.ALL;

    private final List<FileNode> oldBackstages = new ArrayList<>();

    private final Map<Property, String> explicitProperties = new HashMap<>();

    @Remaining
    public void stageOrExplicitProperty(String str) {
        int idx;
        FileNode backstage;
        String key;
        Property property;

        idx = str.indexOf('=');
        if (idx == -1) {
            backstage = oldLib.join("backstages", str);
            if (!backstage.isDirectory()) {
                throw new ArgumentException("old stage not found: " + backstage.getAbsolute());
            }
            oldBackstages.add(backstage);
        } else {
            key = str.substring(0, idx);
            property = StageConfiguration.properties(session.extensionsFactory).get(key);
            if (property == null) {
                throw new ArgumentException("property not found: " + key);
            }
            explicitProperties.put(property, str.substring(idx + 1));
        }
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

        oldLib.checkDirectory();
        if (oldLib.equals(session.lib)) {
            throw new ArgumentException("cannot import from myself");
        }
        console.info.println();
        oldBedroom = Bedroom.loadOrCreateDir(session.gson, oldLib);
        newBedroom = Bedroom.loadOrCreate(session.gson, session.lib);
        newBedroomOrig = newBedroom.stages().toString();
        patches = new ArrayList<>();
        if (include.withConfig) {
            patches.add(stoolConfig());
        }
        if (include.withStages) {
            if (oldBackstages.isEmpty()) {
                for (FileNode oldBackstage : oldLib.join("backstages").list()) {
                    name = oldBackstage.getName();
                    if (session.backstages.join(name).exists()) {
                        console.info.println("ignoring stage that already exists: " + name);
                    } else {
                        oldBackstages.add(oldBackstage);
                    }
                }
            }
            for (FileNode oldBackstage : oldBackstages) {
                name = oldBackstage.getName();
                if (oldBedroom.stages().contains(name)) {
                    newBedroom.add(session.gson, name);
                }
                patches.add(stage(oldBackstage));
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

        dest = session.lib.join(path);
        current = dest.readString();
        result = Transform.transform(oldLib.join(path).readString(), null);// TODO
        diff = Diff.diff(current, result);
        return new Patch("M " + dest.getAbsolute(), diff) {
            public void apply() throws IOException {
                dest.writeString(result);
            }
        };
    }

    private Patch stage(FileNode oldBackstage) throws IOException {
        final FileNode tmpBackstage;
        final FileNode destBackstage;
        FileNode directory;
        final Stage stage;
        String url;
        String msg;
        FileNode tmpConfig;
        String tmp;

        directory = (FileNode) oldBackstage.join("anchor").resolveLink();
        directory.checkDirectory();
        url = Stage.probe(session.subversion(), directory);
        destBackstage = session.backstages.join(oldBackstage.getName());
        destBackstage.checkNotExists();
        // Temp backstage in backstage directory, because it fasted to move within the same filesystem.
        // And Sushi has problems to move the anchor symlink across file systems
        tmpBackstage = session.backstages.createTempDirectory();
        tmpBackstage.deleteDirectory();
        Files.createStoolDirectory(console.verbose, tmpBackstage);
        stage = Stage.createOpt(session, url, session.createStageConfiguration(url), tmpBackstage, directory);
        stage.tuneConfiguration();
        stage.initialize();
        tmpConfig = tmpBackstage.join("config.json");
        tmp = Transform.transform(oldBackstage.join("config.json").readString(), new Upgrade() {}); // TODO: conversion
        tmpConfig.writeString(tmp);
        explicit(tmpConfig);
        msg = Diff.diff(oldBackstage.join("config.json").readString(), tmp);
        if (msg.isEmpty()) {
            // make sure the message is not empty, because we have to move the file
            msg = "(no config changes)";
        }
        return new Patch("A " + destBackstage.getAbsolute(), msg) {
            @Override
            public void apply() throws IOException {
                tmpBackstage.move(destBackstage);
                if (session.configuration.shared) {
                    session.chown(stage.owner(), destBackstage);
                }
            }
        };
    }

    private void explicit(FileNode file) throws IOException {
        StageConfiguration config;

        config = StageConfiguration.load(session.gson, file);
        for (Map.Entry<Property, String> entry : explicitProperties.entrySet()) {
            entry.getKey().set(config, entry.getValue());
        }
        config.save(session.gson, file);
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
