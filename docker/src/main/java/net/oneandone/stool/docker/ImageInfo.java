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
package net.oneandone.stool.docker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ImageInfo {
    private static final String IMAGE_PREFIX = "net.oneandone.stool-";

    public static final String IMAGE_LABEL_CHART = IMAGE_PREFIX + "chart";
    public static final String IMAGE_LABEL_PORT_JMXMP = IMAGE_PREFIX + "port.jmxmp";

    public static final String IMAGE_LABEL_DISK = IMAGE_PREFIX + "disk";
    public static final String IMAGE_LABEL_MEMORY = IMAGE_PREFIX + "memory";
    public static final String IMAGE_LABEL_URL_CONTEXT = IMAGE_PREFIX + "url.context";
    public static final String IMAGE_LABEL_URL_SUFFIXES = IMAGE_PREFIX + "url.suffixes";
    public static final String IMAGE_LABEL_FAULT = IMAGE_PREFIX + "fault";
    public static final String IMAGE_LABEL_COMMENT = IMAGE_PREFIX + "comment";
    public static final String IMAGE_LABEL_ORIGIN_SCM = IMAGE_PREFIX + "origin-scm";
    public static final String IMAGE_LABEL_ORIGIN_USER = IMAGE_PREFIX + "origin-user";
    public static final String IMAGE_LABEL_ARG_PREFIX = IMAGE_PREFIX + "arg.";

    public final String id;
    public final LocalDateTime created;
    public final String author;
    public final List<String> repositoryTags;
    public final Map<String, String> labels;

    public ImageInfo(String id, List<String> repositoryTags, LocalDateTime created, String author, Map<String, String> labels) {
        this.id = id;
        this.created = created;
        this.author = author;
        this.repositoryTags = repositoryTags;
        this.labels = labels;
    }

    public boolean matches(Map<String, String> test) {
        String cmp;

        for (Map.Entry<String, String> entry : test.entrySet()) {
            cmp = labels.get(entry.getKey());
            if (cmp == null || !cmp.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
