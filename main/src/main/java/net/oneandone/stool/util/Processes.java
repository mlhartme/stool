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
import net.oneandone.sushi.util.Separator;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Processes {
    // group 1: pid
    // group 2: pcpu
    // group 3: pmem
    // group 4: command
    private static final Pattern PS_LINE = Pattern.compile("^(\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(.*)$");

    public static Processes load(World world) throws Failure {
        return create(new Launcher(world.getWorking(), "ps", "ax", "-o", "pid=,pcpu=,pmem=,command=").exec());
    }

    public static Processes create(String str) throws Failure {
        Processes result;
        Matcher matcher;
        int pid;
        double cpu;
        double mem;
        String command;

        result = new Processes();
        for (String line : Separator.RAW_LINE.split(str)) {
            line = line.trim();
            matcher = PS_LINE.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(line);
            }
            pid = Integer.parseInt(matcher.group(1));
            cpu = Double.parseDouble(matcher.group(2));
            mem = Double.parseDouble(matcher.group(3));
            command = matcher.group(4);
            result.add(new Data(pid, cpu, mem, command));
        }
        return result;
    }

    public static class Data {
        public final int pid;
        public final double cpu;
        public final double mem;
        public final String command;

        public Data(int pid, double cpu, double mem, String command) {
            this.pid = pid;
            this.cpu = cpu;
            this.mem = mem;
            this.command = command;
        }

        public String toString() {
            return pid + " " + cpu + " " + mem + " " + command;
        }
    }

    //--

    private final Map<String, Data> all;

    public Processes() {
        all = new HashMap<>();
    }

    public void add(Data data) {
        if (all.put(Integer.toString(data.pid), data) != null) {
            throw new IllegalArgumentException(data.toString());
        }
    }

    public Data lookup(String pid) {
        return all.get(pid);
    }

    public boolean hasPid(String pid) {
        return all.containsKey(pid);
    }

    public String servicePid(FileNode backstage) {
        String key;
        int result;

        result = 0;
        key = "wrapper.statusfile=" + backstage.join("shared/run/tomcat.status").getAbsolute();
        for (Data data : all.values()) {
            if (data.command.contains(key)) {
                if (result != 0) {
                    throw new IllegalStateException("ambiguous: " + result + " vs " + data.pid);
                }
                result = data.pid;
            }
        }
        return result == 0 ? null : Integer.toString(result);
    }
}
