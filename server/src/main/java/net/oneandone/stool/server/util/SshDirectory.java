package net.oneandone.stool.server.util;

import net.oneandone.stool.server.Server;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** thread save */
public class SshDirectory {
    private static final String PUB_SUFFIX = ".pub";
    private static final String PUB_PATTERN = "*" + PUB_SUFFIX;
    private static final String AUTHORIZED_KEYS = "authorized_keys";

    public static SshDirectory create(FileNode directory) throws IOException {
        SshDirectory result;

        if (!directory.exists()) {
            Server.LOGGER.info("creating " + directory);
            directory.mkdirs();
        }
        result = new SshDirectory(directory);
        // wipe at startup because count == 0 does not reflect old keys in this directory
        result.reset();
        return result;
    }

    private final FileNode directory;
    private int count;

    private SshDirectory(FileNode directory) {
        this.directory = directory;
        this.count = 0;
    }

    private void reset() throws IOException {
        for (FileNode pub : directory.find(PUB_PATTERN)) {
            pub.deleteFile();
        }
        directory.join(AUTHORIZED_KEYS).writeString("");
        count = 0;
    }

    public synchronized String add(int mappedPort) throws IOException {
        RsaKeyPair pair;

        pair = RsaKeyPair.generate();
        count++;
        directory.join(count + PUB_SUFFIX).writeString(
                "command=\"sleep 60; echo closing\",permitopen=\"localhost:" + mappedPort + "\" " + pair.publicKey("stool-" + count));
        update();
        return pair.privateKey();
    }

    public synchronized void update() throws IOException {
        String str;
        FileNode dest;
        FileNode tmp;

        str = doUpdate();
        dest = directory.join(AUTHORIZED_KEYS);
        if (!dest.readString().equals(str)) {
            tmp = directory.join("tmp");
            tmp.writeString(str);
            // atomic change -- to make sure the ssh daemon doesnt see inconsistent content
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private String doUpdate() throws IOException {
        long oneMinuteAgo;
        StringBuilder keys;

        oneMinuteAgo = System.currentTimeMillis() - (1000 * 60);
        keys = new StringBuilder();
        for (FileNode pub : directory.find(PUB_PATTERN)) {
            if (pub.getLastModified() < oneMinuteAgo) {
                pub.deleteFile();
            } else {
                keys.append(pub.readString());
            }
        }
        return keys.toString();
    }
}
