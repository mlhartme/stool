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

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Processes {
    public static Processes create(World world) throws Failure {
        return new Processes(new Launcher((FileNode) world.getWorking(), "ps", "aux").exec());
    }

    // group 1: user
    // group 2: process id
    // group 3: rest of line
    private static final Pattern PS_AUX_LINE = Pattern.compile("^(\\S+)\\s+(\\d+)\\s+(.+)$", Pattern.MULTILINE);

    private final String ps;

    public Processes(String ps) {
        this.ps = ps;
    }

    public boolean hasPid(String pid) {
        Matcher matcher;


        matcher = PS_AUX_LINE.matcher(ps);
        while (matcher.find()) {
            if (pid.equals(matcher.group(2))) {
                return true;
            }
        }
        return false;
    }

    public String tomcatPid(FileNode stageWrapper) {
        Matcher matcher;
        String result;
        String tmp;
        String key;
        String remaining;

        matcher = PS_AUX_LINE.matcher(ps);
        result = null;
        key = "wrapper.statusfile=" + stageWrapper.join("shared/run/tomcat.status").getAbsolute();
        while (matcher.find()) {
            remaining = matcher.group(3);
            if (remaining.contains(key)) {
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
