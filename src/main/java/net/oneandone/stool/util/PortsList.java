package net.oneandone.stool.util;

import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PortsList {
    public static PortsList used(List<FileNode> wrappers) throws IOException {
        PortsList result;

        result = new PortsList();
        for (FileNode wrapper : wrappers) {
            result.list.addAll(load(wrapper).list);
        }
        return result;
    }

    public static PortsList load(FileNode wrapper) throws IOException {
        PortsList result;

        result = new PortsList();
        result.append(wrapper);
        return result;
    }

    private static FileNode file(FileNode wrapper) {
        return wrapper.join("ports");
    }

    //--

    private final List<Ports> list;

    public PortsList() {
        list = new ArrayList<>();
    }

    public int size() {
        return list.size();
    }

    public int jmx() {
        return get(0).jmx();
    }

    public int stop() {
        return get(0).tomcatStop();
    }

    public int debug() {
        return get(0).debugPort();
    }

    public Ports get(int idx) {
        return list.get(idx);
    }

    public void add(Ports ports) {
        list.add(ports);
    }

    public void save(FileNode wrapper) throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (Ports ports : list) {
            lines.add(Integer.toString(ports.prefix));
        }
        file(wrapper).writeLines(lines);
    }

    public void append(FileNode wrapper) throws IOException {
        FileNode file;

        file = file(wrapper);
        if (file.isFile()) {
            for (String line : file.readLines()) {
                add(new Ports(Integer.parseInt(line.trim())));
            }
        }
    }

    public void checkFree() throws IOException {
        for (Ports ports : list) {
            ports.checkFree();
        }
    }

    public boolean contains(Ports ports) {
        return list.contains(ports);
    }
}
