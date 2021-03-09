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
package net.oneandone.stool.directions;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.LocalSettings;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.util.Versions;
import net.oneandone.sushi.fs.file.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Library {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalSettings.class);

    public static Library fromDirectory(FileNode directory) throws IOException {
        return new Library(directory.getName(), directory.checkDirectory(), "unknown");

    }

    public static Library fromRegistry(PortusRegistry registry, String repository, FileNode exports) throws IOException {
        String name;
        List<String> tags;
        String tag;
        String existing;
        FileNode libraryDir;
        FileNode tagFile;
        FileNode tmp;

        if (repository.contains(":")) {
            throw new ArgumentException("invalid library repository: " + repository);
        }
        name = repository.substring(repository.lastIndexOf('/') + 1);
        tags = sortTags(registry.helmTags(Registry.getRepositoryPath(repository)));
        if (tags.isEmpty()) {
            throw new IOException("no tag for repository " + repository);
        }
        tag = tags.get(tags.size() - 1);
        libraryDir = exports.join(name);
        tagFile = Helm.tagFile(libraryDir);
        if (libraryDir.exists()) {
            existing = tagFile.readString().trim();
            if (!tag.equals(existing)) {
                LOGGER.info("updating library " + name + " " + existing + " -> " + tag);
                libraryDir.deleteTree();
            }
        } else {
            LOGGER.info("loading library " + name + " " + tag);
        }
        if (!libraryDir.exists()) {
            tmp = exports.getWorld().getTemp().createTempDirectory();
            try {
                tmp.exec("oras", "pull", repository + ":" + tag);
                libraryDir.mkdir().exec("tar", "zxf", tmp.join("artifact.tgz").getAbsolute());
                if (!libraryDir.exists()) {
                    throw new IllegalStateException(libraryDir.getAbsolute());
                }
            } finally {
                tmp.deleteTree();
            }
            tagFile.writeString(tag);
        }
        return new Library(name, libraryDir, tag);
    }

    private static List<String> sortTags(List<String> lst) { // TODO: also use for taginfo sorting, that's still based on numbers
        Collections.sort(lst, Versions.CMP);
        return lst;
    }

    //--

    private final String name;
    public final FileNode directory;
    private final String version;

    public Library(String name, FileNode directory, String version) {
        this.name = name;
        this.directory = directory;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public List<Chart> charts() throws IOException {
        List<Chart> result;

        result = new ArrayList<>();
        for (FileNode dir : directory.join("charts").list()) {
            result.add(new Chart(dir, version));
        }
        return result;
    }

    public Chart lookupChart(String chartName) {
        FileNode chart;

        chart = directory.join("charts", chartName);
        return chart.exists() ? new Chart(directory, version) : null;
    }
}
