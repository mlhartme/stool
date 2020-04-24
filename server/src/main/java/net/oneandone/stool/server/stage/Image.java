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
package net.oneandone.stool.server.stage;

import net.oneandone.stool.docker.ImageInfo;
import net.oneandone.stool.docker.Registry;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.server.util.Ports;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Image implements Comparable<Image> {
    public static Image load(Registry registry, PodInfo pod) throws IOException {
        Map<String, Image> all;
        Image result;

        all = loadAll(registry, pod.repositoryTag);
        result = all.get(pod.repositoryTag());
        if (result == null) {
            throw new IllegalStateException("missing image for " + pod.repositoryTag() + ": " + all);
        }
        return result;
    }

    public static Map<String, Image> loadAll(Registry registry, String idOrRepoTag) throws IOException {
        String repositoryTag;
        String id;
        Map<String, Image> result;
        ImageInfo info;

        result = new HashMap<>();
        for (String repository : registry.catalog()) {
            for (String tag : registry.tags(repository)) {
                repositoryTag = "127.0.0.1:31500/" + repository + ":" + tag; // TODO
                info = registry.info(repository, tag);
                id = Strings.removeLeft(info.id, "sha256:");
                if (idOrRepoTag.equals(id) || idOrRepoTag.equals(repositoryTag)) {
                    result.put(repositoryTag, load(registry, repository, tag));
                }
            }
        }
        return result;
    }

    public static Image load(Registry registry, String repository, String tag) throws IOException {
        String repositoryTag;
        Map<String, String> labels;
        LocalDateTime created;
        String id;
        ImageInfo info;

        info = registry.info(repository, tag);
        id = Strings.removeLeft(info.id, "sha256:");
        repositoryTag = "127.0.0.1:31500/" + repository + ":" + tag; // TODO

        created = info.created;
        labels = info.labels;
        return new Image(id, repositoryTag, tag(repositoryTag),
                Ports.fromDeclaredLabels(labels), labels.get(ImageInfo.IMAGE_LABEL_P12),
                disk(labels.get(ImageInfo.IMAGE_LABEL_DISK)), memory(labels.get(ImageInfo.IMAGE_LABEL_MEMORY)),
                context(labels.get(ImageInfo.IMAGE_LABEL_URL_CONTEXT)),
                suffixes(labels.get(ImageInfo.IMAGE_LABEL_URL_SUFFIXES)), labels.get(ImageInfo.IMAGE_LABEL_COMMENT),
                labels.get(ImageInfo.IMAGE_LABEL_ORIGIN_SCM), labels.get(ImageInfo.IMAGE_LABEL_ORIGIN_USER),
                created, labels.get(ImageInfo.IMAGE_LABEL_CREATED_BY), args(labels),
                fault(labels.get(ImageInfo.IMAGE_LABEL_FAULT)));
    }

    private static Map<String, String> args(Map<String, String> labels) {
        Map<String, String> result;
        String key;

        result = new HashMap<>();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            key = entry.getKey();
            if (key.startsWith(ImageInfo.IMAGE_LABEL_ARG_PREFIX)) {
                result.put(key.substring(ImageInfo.IMAGE_LABEL_ARG_PREFIX.length()), entry.getValue());
            }
        }
        return result;
    }

    private static int memory(String memory) {
        return memory == null ? 1024 : Integer.parseInt(memory);
    }

    private static int disk(String disk) {
        return disk == null ? 1024 * 42 : Integer.parseInt(disk);
    }

    private static String context(String context) {
        String result;

        result = context == null ? "" : context;
        if (result.startsWith("/")) {
            throw new ArithmeticException("server must not start with '/': " + result);
        }
        if (!result.isEmpty() && result.endsWith("/")) {
                throw new ArithmeticException("server must not end with '/': " + result);
        }
        return result;
    }

    private static final Separator SUFFIXES_SEP = Separator.on(',').trim();

    private static List<String> suffixes(String suffixes) {
        List<String> result;

        result = new ArrayList<>();
        if (suffixes != null) {
            result.addAll(SUFFIXES_SEP.split(suffixes));
        }
        if (result.isEmpty()) {
            result.add("");
        }
        return result;
    }

    private static List<String> fault(String fault) {
        List<String> result;

        result = new ArrayList<>();
        if (fault != null) {
            result.addAll(Separator.COMMA.split(fault));
        }
        return result;
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

    public final String id;
    public final String repositoryTag;
    public final String tag;
    /** parsed version, null if version is not a number */
    public final Integer tagNumber;

    //-- meta data

    public final Ports ports;

    public final String p12;

    /** in megabytes */
    public final int disk;

    /** memory in megabytes */
    public final int memory;

    public final String urlContext;
    public final List<String> urlSuffixes;

    /** docker api returns a comment field, but i didn't find documentation how to set it */
    public final String comment;
    public final String originScm;
    public final String originUser;
    public final LocalDateTime createdAt;
    public final String createdBy;
    public final Map<String, String> args;

    /** maps relative host path to absolute container path */
    public final List<String> faultProjects;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public Image(String id, String repositoryTag, String tag, Ports ports, String p12, int disk, int memory, String urlContext, List<String> urlSuffixes, String comment,
                 String originScm, String originUser, LocalDateTime createdAt, String createdBy, Map<String, String> args, List<String> faultProjects) {
        if (!urlContext.isEmpty()) {
            if (urlContext.startsWith("/") || urlContext.endsWith("/")) {
                throw new IllegalArgumentException(urlContext);
            }
        }
        this.id = id;
        this.repositoryTag = repositoryTag;
        this.tag = tag;
        this.tagNumber = parseOpt(tag);

        this.ports = ports;
        this.p12 = p12;
        this.disk = disk;
        this.memory = memory;
        this.urlContext = urlContext;
        this.urlSuffixes = urlSuffixes;
        this.comment = comment;
        this.originScm = originScm;
        this.originUser = originUser;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.args = args;
        this.faultProjects = faultProjects;
    }

    private static Integer parseOpt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public int compareTo(Image o) {
        if (tagNumber != null && o.tagNumber != null) {
            return tagNumber.compareTo(o.tagNumber);
        } else if (tagNumber == null && o.tagNumber == null) {
            return tag.compareTo(o.tag);
        } else {
            return tagNumber != null ? -1 : 1;
        }
    }

    public String toString() {
        return repositoryTag + " " + createdAt.toString();
    }

    //--

    public static int nextTag(List<Image> images) {
        Image image;

        for (int i = images.size() - 1; i >= 0; i--) {
            image = images.get(i);
            if (image.tagNumber != null) {
                return image.tagNumber + 1;
            }
        }
        return 1;
    }
}
