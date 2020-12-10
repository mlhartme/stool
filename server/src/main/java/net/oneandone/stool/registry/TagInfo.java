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

import net.oneandone.stool.docker.ImageInfo;
import net.oneandone.sushi.util.Separator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TagInfo implements Comparable<TagInfo> {
    public static TagInfo create(String id, String repositoryTag, String tag, String author, LocalDateTime created, Map<String, String> labels) {
        return new TagInfo(id, repositoryTag, tag, author,
                context(labels.get(ImageInfo.IMAGE_LABEL_URL_CONTEXT)),
                suffixes(labels.get(ImageInfo.IMAGE_LABEL_URL_SUFFIXES)),
                created, labels);
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

    //--

    public final String id; // includes "sha256:" prefix

    public final String repositoryTag;
    public final String tag;
    /** parsed version, null if version is not a number */
    public final Integer tagNumber;
    public final String author;
    public final LocalDateTime createdAt;
    public final Map<String, String> labels;

    //-- meta data

    public final String urlContext;
    public final List<String> urlSuffixes;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public TagInfo(String id, String repositoryTag, String tag, String author, String urlContext, List<String> urlSuffixes,
                   LocalDateTime createdAt, Map<String, String> labels) {
        if (!urlContext.isEmpty()) {
            if (urlContext.startsWith("/") || urlContext.endsWith("/")) {
                throw new IllegalArgumentException(urlContext);
            }
        }
        this.id = id;
        this.repositoryTag = repositoryTag;
        this.tag = tag;
        this.tagNumber = parseOpt(tag);
        this.author = author;

        this.urlContext = urlContext;
        this.urlSuffixes = urlSuffixes;
        this.createdAt = createdAt;
        this.labels = labels;
    }

    private static Integer parseOpt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public int compareTo(TagInfo o) {
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
}
