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

import net.oneandone.sushi.fs.file.FileNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Processes {
    // group 1: user
    // group 2: prozess id
    // group 3: der rest der Zeile
    private static final Pattern TOMCAT_PATTERN = Pattern.compile("^(\\S+)\\s+(\\d+)\\s+(.+)$", Pattern.MULTILINE);

    private final String ps;

    public Processes(String ps) {
        this.ps = ps;
    }

    public String tomcatPid(FileNode stageWrapper) {
        Matcher matcher;
        String result;
        String tmp;
        String key;
        String rest;

        matcher = TOMCAT_PATTERN.matcher(ps);
        result = null;
        key = "wrapper.statusfile=" + stageWrapper.join("shared/run/tomcat.status").getAbsolute();
        while (matcher.find()) {
            rest = matcher.group(3);
            if (rest.contains(key)) {
                tmp = matcher.group(2);
                if (result == null) {
                    result = tmp;
                } else {
                    throw new IllegalStateException("ambiguous: " + result + " vs " + matcher.group(2));
                }
            }
        }
        return result;
    }
}
