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

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Ports {
    private static FileNode file(FileNode wrapper) {
        return wrapper.join("ports");
    }

    public static void save(FileNode wrapper, List<Ports> ports) throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (Ports p : ports) {
            lines.add(Integer.toString(p.prefix));
        }
        file(wrapper).writeLines(lines);
    }

    public static List<Ports> load(FileNode wrapper) throws IOException {
        List<Ports> result;
        FileNode file;

        file = file(wrapper);
        result = new ArrayList<>();
        if (file.isFile()) {
            for (String line : file.readLines()) {
                result.add(new Ports(Integer.parseInt(line.trim())));
            }
        }
        return result;
    }

    //--

    public static Ports forName(String name, Ports first, Ports last) {
        return new Ports((Math.abs(name.hashCode()) % (last.prefix - first.prefix + 1)) + first.prefix);
    }

    private final int prefix;

    public Ports(int prefix) {
        this.prefix = prefix;
    }

    public int prefix() {
        return prefix;
    }

    public Ports next() {
        return new Ports(prefix + 1);
    }

    public int tomcatHttp() {
        return prefix * 10;
    }

    public int tomcatStop() {
        return prefix * 10 + 1;
    }

    public int tomcatHttps() {
        return prefix * 10 + 3;
    }

    public int debugPort() {
        return prefix * 10 + 5;
    }

    public int jmx() {
        return prefix * 10 + 6;
    }

    //--

    public String toString() {
        return Integer.toString(prefix);
    }

    public int hashCode() {
        return prefix;
    }

    public boolean equals(Object obj) {
        if (obj instanceof Ports) {
            return prefix == ((Ports) obj).prefix;
        }
        return false;
    }

    public boolean within(Ports first, Ports last) {
        return prefix >= first.prefix && prefix <= last.prefix;
    }

    //--

    // TODO: for stool config ...
    public static class PortsTypeAdapter extends TypeAdapter<Ports> {
        @Override
        public void write(JsonWriter out, Ports value) throws IOException {
            out.value(value.prefix);
        }

        @Override
        public Ports read(JsonReader in) throws IOException {
            return new Ports(in.nextInt());
        }
    }
}
