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
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * http://stackoverflow.com/questions/4823468/comments-in-markdown
 */
public class Markdown {
    public static void main(String[] args) throws IOException {
        World world;
        FileNode src;
        FileNode dest;
        FileNode man;
        List<String> lines;
        Map<String, List<String>> variables;

        if (args.length != 3) {
            throw new IOException("usage: markdown <src> <dest> <man>");
        }
        world = World.create();
        src = world.file(args[0]);
        src.checkFile();
        dest = world.file(args[1]);
        dest.getParent().checkDirectory();
        man = world.file(args[2]);
        man.mkdirsOpt();
        lines = load(src);
        variables = new HashMap<>();
        variables.put("ALL_SYNOPSIS", synopsis(lines));
        lines = substitute(lines, variables);
        checkCrossReferences(lines);
        manpages(lines, man);
        lines = inlines(lines, variables);
        dest.writeLines(lines);
    }

    private static void checkCrossReferences(List<String> lines) throws IOException {
        List<String> labels;
        int depth;
        int start;
        int end;
        int last;
        String l;

        labels = new ArrayList<>();
        for (String line : lines) {
            depth = depth(line);
            if (depth > 0) {
                labels.add(toLabel(line.substring(depth)));
            }
        }
        for (String line : lines) {
            last = 0;
            while (true) {
                start = line.indexOf("](#", last);
                if (start == -1) {
                    break;
                }
                start += 3;
                end = line.indexOf(')', start);
                if (end == -1) {
                    throw new IOException("missing )");
                }
                l = line.substring(start, end);
                if (!labels.contains(l)) {
                    System.out.println("cross reference not found: " + l);
                }
                last = end + 1;
            }
        }
    }

    private static String toLabel(String str) {
        str = str.trim();
        str = str.toLowerCase();
        return str.replace(' ', '-');
    }

    private static void manpages(List<String> lines, FileNode dir) throws IOException {
        String lastContent;
        Manpage manpage;
        FileNode roff;
        Launcher launcher;
        List<FileNode> ronns;

        lastContent = null;
        manpage = null;
        for (String line : lines) {
            if (manpage == null) {
                manpage = Manpage.start(dir, line, lastContent);
            } else {
                manpage = manpage.end(line);
            }
            if (manpage != null) {
                manpage.line(line);
            }
            if (!line.isEmpty()) {
                lastContent = line;
            }
        }
        System.out.println("dir: " + dir.getAbsolute());
        ronns = dir.find("*.ronn");
        launcher = dir.launcher("ronn", "--roff");
        for (FileNode file :ronns) {
            launcher.arg(file.getName());
        }
        System.out.println(launcher.exec());
        for (FileNode file : ronns) {
            file.deleteFile();
            roff = file.getParent().join(Strings.removeRight(file.getName(), ".ronn"));
            roff.gzip(roff.getParent().join(roff.getName() + ".gz"));
            roff.deleteFile();
        }
    }

    public static List<String> load(FileNode src) throws IOException {
        List<String> result;
        List<FileNode> stack;

        result = new ArrayList<>();
        stack = new ArrayList<>();
        load(src, stack, result);
        if (!stack.isEmpty()) {
            throw new IllegalStateException();
        }
        return result;
    }

    public static void load(FileNode src, List<FileNode> stack, List<String> result) throws IOException {
        String name;

        src.checkFile();
        if (stack.contains(src)) {
            throw new IOException("circular includes: " + stack);
        }
        stack.add(src);
        for (String line : src.readLines()) {
            if (line.startsWith("!INCLUDE ")) {
                name = line.substring(9);
                name = Strings.removeLeft(name, "\"");
                name = Strings.removeRight(name, "\"");
                load(src.getParent().join(name), stack, result);
            } else {
                result.add(line);
            }
        }
        stack.remove(stack.size() - 1);
    }

    private static List<String> synopsis(List<String> lines) {
        int count;
        boolean collect;
        List<String> result;

        result = new ArrayList<>();
        collect = false;
        for (String line : lines) {
            count = depth(line);
            if (count > 0) {
                collect = isSynopsis(line);
            } else {
                if (collect) {
                    result.add(line);
                }
            }
        }
        return result;
    }

    private static List<String> substitute(List<String> lines, Map<String, List<String>> variables) throws IOException {
        List<String> result;
        List<String> l;

        result = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line.startsWith("%")) {
                l = variables.get(line.substring(1));
                if (l == null) {
                    throw new IOException("not found: " + line);
                }
                result.addAll(l);
            } else {
                result.add(line);
            }
        }
        return result;
    }

    private static List<String> inlines(List<String> lines, Map<String, List<String>> variables) throws IOException {
        String startLine;
        String endLine;
        List<String> result;
        String key;
        List<String> value;
        int next;

        result = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            startLine = lines.get(i);
            if (isAction(startLine)) {
                key = getActionCode(startLine);
                value = variables.get(key);
                if (value == null) {
                    throw new IOException("not found: " + key);
                }
                next = nextAction(lines, i + 1);
                if (next == -1) {
                    throw new IOException("missing end marker for action " + key);
                }
                endLine = lines.get(next);
                if (!"-".equals(getActionCode(endLine))) {
                    throw new IOException("unexpected end marker for action " + key + ": " + getActionCode(endLine));
                }
                result.add(startLine);
                result.addAll(value);
                result.add(endLine);
                i = next + 1;
            }
        }
        return result;
    }

    private static int nextAction(List<String> lines, int start) {
        for (int i = start; i < lines.size(); i++) {
            if (isAction(lines.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isAction(String line) {
        return line.startsWith("[//]");
    }

    private static String getActionCode(String line) throws IOException {
        final String start = "[//]: # (";

        if (!line.startsWith(start) || !line.endsWith(")")) {
            throw new IOException("invalid action line: " + line);
        }
        return line.substring(start.length(), line.length() - 1);
    }

    private static int depth(String header) {
        int count;

        count = 0;
        for (int i = 0, max = header.length(); i < max; i++) {
            if (header.charAt(i) != '#') {
                return count;
            }
            count++;
        }
        return count;
    }

    private static String trimHeader(String header) {
        return header.substring(depth(header)).trim();
    }

    private static boolean isSynopsis(String line) {
        return line.endsWith("# SYNOPSIS");
    }

    public static class Manpage {
        public static Manpage start(FileNode dir, String line, String lastContent) throws IOException {
            int depth;
            int idx;
            String name;
            Writer dest;
            Manpage result;
            FileNode ronn;

            if (!isSynopsis(line)) {
                return null;
            }
            if (lastContent == null) {
                throw new IOException("missing man header for line:" + line);
            }
            depth = depth(line) - 1;
            if (depth(lastContent) != depth) {
                throw new IOException("nesting error:" + lastContent + " vs " + line);
            }
            idx = lastContent.indexOf(" -- ");
            if (idx == -1) {
                throw new IOException("missing separator: " + lastContent);
            }
            name = trimHeader(lastContent.substring(0, idx));
            ronn = dir.join(name + ".1.ronn");
            dest = ronn.newWriter();
            result = new Manpage(depth, ronn, dest);
            result.line(lastContent);
            result.line();
            return result;
        }

        private final int depth;
        private final FileNode file;
        private final Writer dest;

        public Manpage(int depth, FileNode file, Writer dest) {
            this.depth = depth;
            this.file = file;
            this.dest = dest;
        }

        public void line() throws IOException {
            line("");
        }

        public void line(String line) throws IOException {
            int count;

            count = Markdown.depth(line);
            if (count > 0) {
                line = line.substring(depth - 1);
            }
            dest.write(line);
            dest.write('\n');
        }

        public Manpage end(String line) throws IOException {
            if (line.startsWith("#") && depth(line) <= depth) {
                dest.close();
                return null;
            } else {
                return this;
            }
        }
    }
}