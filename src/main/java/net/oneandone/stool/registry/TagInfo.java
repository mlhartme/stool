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

import java.time.LocalDateTime;
import java.util.Map;

public class TagInfo implements Comparable<TagInfo> {
    public static TagInfo create(String id, String repositoryTag, String tag, String author, LocalDateTime created, Map<String, String> labels) {
        return new TagInfo(id, repositoryTag, tag, author, created, labels);
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

    @SuppressWarnings("checkstyle:ParameterNumber")
    public TagInfo(String id, String repositoryTag, String tag, String author, LocalDateTime createdAt, Map<String, String> labels) {
        this.id = id;
        this.repositoryTag = repositoryTag;
        this.tag = tag;
        this.tagNumber = parseOpt(tag);
        this.author = author;
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
