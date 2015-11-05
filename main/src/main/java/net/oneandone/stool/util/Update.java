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
package net.oneandone.stool.util;

import com.github.zafarkhaja.semver.Version;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.stool.Start;
import net.oneandone.stool.setup.Util;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class Update {
    public static void main(String[] args) throws IOException {
        System.out.println("releases " + list(Console.create(new World())));
    }

    public static List<FileNode> check(Console console, Version current, FileNode home, FileNode downloads) throws IOException {
        String version;
        Version next;
        FileNode script;
        FileNode download;
        String args;
        List<FileNode> result;

        result = new ArrayList<>();
        for (Release release : list(console)) {
            version = release.name;
            version = Strings.removeLeft(version, "setup-stool-");
            version = Strings.removeRight(version, ".sh");
            next = Version.valueOf(version);
            if (next.greaterThan(current)) {
                script = downloads.join("update-stool-" + version + ".sh");
                if (!script.isFile()) {
                    download = downloads.join("setup-stool-" + version + ".sh");
                    if (current.getMajorVersion() == next.getMajorVersion() && current.getMinorVersion() == next.getMinorVersion()) {
                        args = home.getAbsolute();
                    } else {
                        args = home.getAbsolute() + " " + home.getParent().join("stool-"
                                + next.getMajorVersion() + "." + next.getMinorVersion()).getAbsolute();
                    }
                    script.writeLines(
                            "#!/bin/sh",
                            "echo Downloading " + release.name + " ...",
                            "curl -L --fail --silent --show-error -o" + download.getAbsolute() + " " + release.url,
                            "chmod a+x " + download.getAbsolute(),
                            download.getAbsolute() + " " + args
                    );
                    script.setPermissions("rwxrwxr-x");
                    result.add(script);
                }
            }
        }
        return result;
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static List<Release> list(Console console) throws IOException {
        JsonArray releases;
        FileNode tmp;
        List<Release> result;
        JsonObject release;
        JsonArray assets;
        JsonObject asset;
        LocalDateTime date;

        result = new ArrayList<>();
        tmp = console.world.getTemp().createTempFile();
        Util.downloadFile(console.verbose, "https://api.github.com/repos/mlhartme/stool/releases", tmp);
        releases = (JsonArray) new JsonParser().parse(tmp.readString());
        for (JsonElement element : releases) {
            release = (JsonObject) element;
            assets = (JsonArray) release.get("assets");
            if (assets.size() != 1) {
                throw new IllegalStateException(assets.toString());
            }
            asset = (JsonObject) assets.get(0);
            try {
                date = LocalDateTime.parse(asset.get("created_at").getAsString(), FMT);
            } catch (DateTimeParseException e) {
                throw new IOException("invalid date: " + e.getMessage(), e);
            }
            result.add(new Release(asset.get("name").getAsString(), date, asset.get("browser_download_url").getAsString()));
        }
        return result;
    }

    public static class Release {
        public final String name;
        public final LocalDateTime date;
        public final String url;

        public Release(String name, LocalDateTime date, String url) {
            this.name = name;
            this.date = date;
            this.url = url;
        }

        public String toString() {
            return name + " " + date + " " + url;
        }
    }
}
