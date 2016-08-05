package net.oneandone.stool.site;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class Markdown {
    public static void main(String[] args) throws IOException {
        World world;
        FileNode src;
        FileNode dir;
        String lastContent;
        Manpage manpage;
        Writer all;

        if (args.length != 1) {
            throw new IOException("usage: markdown <src>");
        }
        world = World.create();
        src = world.file(args[0]);
        src.checkFile();
        lastContent = null;
        manpage = null;
        dir = src.getParent();
        all = dir.join("all").newWriter();
        for (String line : src.readLines()) {
            line = line.trim();
            if (manpage == null) {
                manpage = Manpage.start(dir, line, lastContent);
            } else {
                manpage = manpage.end(line, all);
            }
            if (manpage != null) {
                manpage.line(line);
            }
            if (!line.isEmpty()) {
                lastContent = line;
            }
        }
        all.close();
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

    public static class Manpage {
        public static Manpage start(FileNode dir, String line, String lastContent) throws IOException {
            int depth;
            int idx;
            String name;
            Writer dest;
            Manpage result;


            if (!line.endsWith("# SYNOPSIS")) {
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
            result.synopsis = true;
            result.line();
            return result;
        }

        private final int depth;
        private final Writer dest;
        private final List<String> synopsis;
        private boolean inSynopsis;

        public Manpage(int depth, Writer dest) {
            this.depth = depth;
            this.dest = dest;
            this.synopsis = new ArrayList<>();
            this.inSynopsis = false;
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
            if (inSynopsis) {
                if (!line.isEmpty()) {
                    if (count == 0) {
                        synopsis.add(line);
                    } else {
                        inSynopsis = false;
                    }
                }
            }
        }

        public Manpage end(String line, Writer all) throws IOException {
            if (line.startsWith("#") && depth(line) < depth) {
                dest.close();
                for (String s : synopsis) {
                    all.write(s);
                    all.write('\n');
                }
                return null;
            } else {
                return this;
            }
        }
    }
}