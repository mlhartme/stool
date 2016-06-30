package net.oneandone.stool.ssl;

import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Pair {
    private final FileNode privateKey;
    private final FileNode certificate;

    public Pair(FileNode privateKey, FileNode certificate) {
        this.privateKey = privateKey;
        this.certificate = certificate;
    }

    public FileNode privateKey() {
        return privateKey;
    }

    public FileNode certificate() {
        return certificate;
    }

    public String text() throws IOException {
        return privateKey.readString() + "\n" + certificate.readString();
    }
}
