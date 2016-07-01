package net.oneandone.stool.ssl;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpNode;

import java.io.IOException;

public class FromCsr {
    public static Pair create(FileNode workDir, String url, String hostname) throws IOException {
        World world;
        FileNode csr;
        FileNode cert;
        FileNode key;
        HttpNode node;

        csr = workDir.join("csr.pem");
        cert = workDir.join("cert.pem");
        key = workDir.join("key.pem");
        workDir.exec("openssl", "req", "-new", "-newkey", "rsa:2048", "-nodes", "-subj", "/CN=" + hostname,
                "-keyout", key.getAbsolute(), "-out", csr.getAbsolute());
        world = workDir.getWorld();
        node = (HttpNode) world.validNode(url);
        node.getRoot().addExtraHeader("Content-Type", "text/plain");
        cert.writeBytes(node.post(csr.readBytes()));
        return new Pair(key, cert);
    }
}
