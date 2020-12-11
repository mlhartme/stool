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
package net.oneandone.stool.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.sushi.fs.FileNotFoundException;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry API I need for Stool. I didn't find a way to query tag authors with Docker Registry API V2, so prerequisite is Portus for now.
 * But I keep the Docker implementation to preserve knowledge, and maybe I find a ways later ...
 */
public abstract class Registry {
    /** @return list of repositories */
    public abstract List<String> list() throws IOException;
    public abstract void delete(String repository) throws IOException;
    public abstract List<String> tags(String repository) throws IOException;

    public abstract TagInfo info(String repository, String tag) throws IOException;

    //--

    public static List<String> toList(JsonArray array) {
        List<String> result;

        result = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            result.add(element.getAsString());
        }
        return result;
    }

    public static Map<String, String> toMap(JsonObject object) {
        Map<String, String> result;

        result = new LinkedHashMap<>(object.size());
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    public static String getArgument(String header, String arg) throws IOException {
        int idx;
        int len;
        int end;

        idx = header.indexOf(arg);
        if (idx == -1) {
            throw new IOException("argument '" + arg + "' not found in header: " + header);
        }
        len = arg.length();
        if (header.indexOf("=\"", idx + len) != idx + len) {
            throw new IOException("argument '" + arg + "' not properly quoted: " + header);
        }
        end = header.indexOf('"', idx + len + 2);
        if (end == -1) {
            throw new IOException("argument '" + arg + "' not terminated: " + header);
        }
        return header.substring(idx + len + 2, end);
    }

    //--

    /** @return sorted list, oldest first */
    public List<TagInfo> list(String repositoryPath) throws IOException {
        List<String> tags;
        List<TagInfo> result;

        result = new ArrayList<>();
        try {
            tags = tags(repositoryPath);
        } catch (net.oneandone.sushi.fs.FileNotFoundException e) {
            return result;
        }
        for (String tag : tags) {
            result.add(info(repositoryPath, tag));
        }
        Collections.sort(result);
        return result;
    }

    public TagInfo tagInfo(String image) throws IOException {
        String tag;
        String repositoy;

        tag = image.substring(image.lastIndexOf(':') + 1);
        repositoy = getRepositoryPath(toRepository(image));
        return info(repositoy, tag);
    }

    //-- utils

    // without hostname
    public static String getRepositoryPath(String repository) {
        String path;

        path = URI.create(repository).getPath();
        path = path.substring(path.indexOf('/') + 1);
        return path;
    }

    public static String toRepository(String imageOrRepository) {
        int idx;

        idx = imageOrRepository.indexOf(':');
        return idx == -1 ? imageOrRepository : imageOrRepository.substring(0, idx);
    }

    //--

    public TagInfo resolve(String imageOrRepository) throws IOException {
        int idx;

        idx = imageOrRepository.indexOf(':');
        if (idx == -1) {
            List<TagInfo> all;

            all = list(Registry.getRepositoryPath(imageOrRepository));
            if (all.isEmpty()) {
                throw new ArgumentException("no image(s) found in repository " + imageOrRepository);
            }
            return all.get(all.size() - 1);
        } else {
            try {
                return tagInfo(imageOrRepository);
            } catch (FileNotFoundException e) {
                throw new ArgumentException("image not found: " + imageOrRepository);
            }
        }
    }

}
