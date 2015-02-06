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

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;

import java.io.IOException;

public class SSLKeyStore {
    private final FileNode file;

    public SSLKeyStore(FileNode workDir) {
        file = workDir.join("tomcat.jks");
    }

    public void download(String hostname) throws IOException {
        CertificateAuthority ca;

        ca = new CertificateAuthority(file.getParent(), hostname);
        pkcs12toKeyStore(pkcs12Store(ca.certificate()));
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

    private void pkcs12toKeyStore(FileNode pkcs12) throws IOException {
        try {
            file.getParent().launcher("keytool", "-importkeystore", "-srckeystore", pkcs12.getAbsolute(), "-srcstoretype",
              "pkcs12", "-destkeystore", file.getAbsolute(), "-deststoretype", "jks",
              "-deststorepass", password(), "-srcstorepass", password()).exec();
            Files.stoolFile(file);
        } catch (Failure failure) {
            throw new IOException(failure);
        }
    }

    private FileNode pkcs12Store(Certificate certificate) throws IOException {
        FileNode keystore;

        keystore = file.getParent().join("tomcat.p12");
        try {
            file.getParent().launcher("openssl", "pkcs12",
              "-export", "-passout", "pass:" + password(), "-in", certificate.certificate().getAbsolute(),
              "-inkey", certificate.privateKey().getAbsolute(), "-out", keystore.getAbsolute(),
              "-name", "tomcat").exec();
            return keystore;
        } catch (Failure e) {
            throw new IOException(e);
        }
    }
}
