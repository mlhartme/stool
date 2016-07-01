package net.oneandone.stool.ssl;

import net.oneandone.stool.util.Files;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;
import java.io.StringWriter;

public class KeyStore {
    public static KeyStore create(String url, String hostname, FileNode workDir) throws IOException {
        Pair pair;
        KeyStore keyStore;

        keyStore = new KeyStore(workDir);
        if (!keyStore.exists()) {
            if (url.isEmpty()) {
                pair = SelfSigned.create(workDir, hostname);
            } else if (url.equals(Itca.URL_PREFIX)) {
                pair = Itca.create(workDir, hostname);
            } else {
                pair = FromCsr.create(workDir, url, hostname);
            }
            Files.stoolFile(pair.privateKey());
            Files.stoolFile(pair.certificate());
            keyStore.fill(pair);
        }
        return keyStore;
    }

    private final FileNode workDir;
    private final FileNode file;

    public KeyStore(FileNode workDir) {
        this.workDir = workDir;
        this.file = workDir.join("tomcat.jks");
    }

    public void fill(Pair pair) throws IOException {
        addPkcs12(convertToPkcs12(pair));
    }

    public String file() {
        return file.getAbsolute();
    }

    public String type() {
        return "JKS";
    }

    public String password() {
        return "changeit";
    }

    public boolean exists() {
        return file.exists();
    }

    private void addPkcs12(FileNode pkcs12) throws IOException {
        try {
            workDir.launcher("keytool", "-importkeystore", "-srckeystore", pkcs12.getAbsolute(), "-srcstoretype",
                    "pkcs12", "-destkeystore", file.getAbsolute(), "-deststoretype", "jks",
                    "-deststorepass", password(), "-srcstorepass", password()).exec();
            Files.stoolFile(file);
        } catch (Failure failure) {
            throw new IOException(failure);
        }
    }

    // https://en.wikipedia.org/wiki/PKCS_12
    private FileNode convertToPkcs12(Pair pair) throws IOException {
        FileNode pkcs12;

        pkcs12 = workDir.join("tomcat.p12");
        try {
            workDir.launcher("openssl", "pkcs12",
                    "-export", "-passout", "pass:" + password(), "-in", pair.certificate().getAbsolute(),
                    "-inkey", pair.privateKey().getAbsolute(), "-out", pkcs12.getAbsolute(),
                    "-name", "tomcat").exec();
            Files.stoolFile(pkcs12);
            return pkcs12;
        } catch (Failure e) {
            throw new IOException(e);
        }
    }
}
