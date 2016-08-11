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
package net.oneandone.stool.ssl;

import net.oneandone.stool.util.Files;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;

public class KeyStore {
    public static KeyStore create(String url, String hostname, FileNode workDir) throws IOException {
        KeyStore keyStore;

        keyStore = new KeyStore(workDir);
        if (!keyStore.exists()) {
            keyStore.fill(Pair.create(url, hostname, workDir));
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
        addPkcs12(importPkcs12(pair));
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
    // https://tomcat.apache.org/tomcat-7.0-doc/ssl-howto.html
    private FileNode importPkcs12(Pair pair) throws IOException {
        FileNode pkcs12;
        Launcher launcher;

        pkcs12 = workDir.join("tomcat.p12");
        try {
            launcher = workDir.launcher("openssl", "pkcs12",
                    "-export", "-passout", "pass:" + password(), "-in", pair.certificate().getAbsolute(),
                    "-inkey", pair.privateKey().getAbsolute(), "-out", pkcs12.getAbsolute(),
                    "-name", "tomcat");
            if (pair.getIntermediateOpt() != null) {
                launcher.arg("-CAfile", pair.getIntermediateOpt().getAbsolute(), "-caname", "root", "-chain");
            }
            launcher.exec();
            Files.stoolFile(pkcs12);
            return pkcs12;
        } catch (Failure e) {
            throw new IOException(e);
        }
    }
}
