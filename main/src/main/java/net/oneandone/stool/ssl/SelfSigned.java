package net.oneandone.stool.ssl;

import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class SelfSigned {
    public static Pair create(FileNode workDir, String hostname) throws IOException {
        FileNode cert;
        FileNode key;

        cert = workDir.join("cert.pem");
        key = workDir.join("key.pem");
        workDir.exec("openssl", "req", "-x509", "-newkey", "rsa:2048", "-keyout", key.getAbsolute(), "-out", cert.getAbsolute(),
                "-days", "365", "-nodes", "-subj", "/CN=" + hostname);
        return new Pair(key, cert);
    }
}
