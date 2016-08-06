package net.oneandone.stool.site;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Markdown {
    public static void main(String[] args) throws IOException {
        World world;
        FileNode src;
        FileNode dest;
        List<String> lines;
        Map<String, List<String>> variables;

        if (args.length != 2) {
            throw new IOException("usage: markdown <src> <dest>");
        }
        world = World.create();
        src = world.file(args[0]);
        src.checkFile();
        dest = world.file(args[1]);
        dest.checkDirectory();
        lines = load(src);
        variables = new HashMap<>();
        variables.put("%ALL_SYNOPSIS", synopsis(lines));
        lines = substitute(lines, variables);
        manpages(lines, dest);
        dest.join(src.getName()).writeLines(lines);
    }

    private static void manpages(List<String> lines, FileNode dest) throws IOException {
        String lastContent;
        Manpage manpage;

        lastContent = null;
        manpage = null;
        for (String line : lines) {
            if (manpage == null) {
                manpage = Manpage.start(dest, line, lastContent);
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
                if (collect && !line.isEmpty()) {
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
            dest = dir.join(name + ".1.ronn").newWriter();
            result = new Manpage(depth, dest);
            result.line(lastContent);
            result.line();
            return result;
        }

        private final int depth;
        private final Writer dest;

        public Manpage(int depth, Writer dest) {
            this.depth = depth;
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