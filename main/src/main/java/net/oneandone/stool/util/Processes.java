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
package net.oneandone.stool.util;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;

import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Processes {
    // group 1: pid
    // group 2: ppid
    // group 3: pcpu
    // group 4: pmem
    // group 5: command
    private static final Pattern PS_LINE = Pattern.compile("^(\\d+)\\s+(\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(.*)$");

    public static Processes load(World world) throws Failure {
        Launcher launcher;

        launcher = new Launcher(world.getWorking(), "ps", "ax", "-o", "pid=,ppid=,pcpu=,pmem=,command=");
        // make sure we get doubles with ".", not ",":
        launcher.env("LC_ALL", "C");
        return create(launcher.exec());
    }

    public static Processes create(String str) {
        Processes result;
        Matcher matcher;
        int pid;

        result = new Processes();
        for (String line : Separator.RAW_LINE.split(str)) {
            line = line.trim();
            matcher = PS_LINE.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(line);
            }
            pid = Integer.parseInt(matcher.group(1));
            result.add(pid);
        }
        return result;
    }

    private final Collection<Integer> all;

    public Processes() {
        all = new HashSet<>();
    }

    public void add(int pid) {
        if (!all.add(pid)) {
            throw new IllegalArgumentException("" + pid);
        }
    }

    public boolean hasPid(int pid) {
        return all.contains(pid);
    }
}
