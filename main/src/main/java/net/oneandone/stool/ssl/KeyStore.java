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

import java.io.IOException;

public class KeyStore {
    public static KeyStore create(String scriptOrUrl, String hostname, FileNode workDir) throws IOException {
        KeyStore keyStore;

        keyStore = new KeyStore(workDir);
        if (!keyStore.exists()) {
            keyStore.fill(Pair.create(scriptOrUrl, hostname, workDir));
        }
        return keyStore;
    }

    private final FileNode workDir;
    private final FileNode file;

    public KeyStore(FileNode workDir) {
        this.workDir = workDir;
        this.file = workDir.join("tomcat.jks");
    }

    // https://en.wikipedia.org/wiki/PKCS_12
    // https://tomcat.apache.org/tomcat-7.0-doc/ssl-howto.html
    public void fill(Pair pair) throws IOException {
        FileNode tmp;

        if (pair.isScript()) {
            // nothing to do - already generated by script
            file.checkFile();
        } else {
            tmp = workDir.join("tomcat.p12");
            // keytool cannot import private keys with certs. As a work-around, we create a p12 keystore form them and then import
            // this keystore into a Java keystore.
            workDir.exec("openssl", "pkcs12",
                    "-export", "-passout", "pass:" + password(), "-in", pair.certificate().getAbsolute(),
                    "-inkey", pair.privateKey().getAbsolute(), "-out", tmp.getAbsolute(),
                    "-name", "tomcat");
            workDir.launcher("keytool", "-importkeystore", "-srckeystore", tmp.getAbsolute(), "-srcstoretype",
                    "pkcs12", "-destkeystore", file.getAbsolute(), "-deststoretype", "jks",
                    "-deststorepass", password(), "-srcstorepass", password()).exec();
            tmp.deleteFile();
        }
        Files.stoolFile(file);
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
}
