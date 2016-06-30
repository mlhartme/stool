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
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;
import java.io.StringWriter;

public class KeyStore {
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

    //--

    public Pair pair(String getUrlStart, String hostname) throws IOException {
        Pair pair;

        pair = newPair(hostname);
        if (!(pair.privateKey().exists() || pair.certificate().exists())) {
            generate(getUrlStart + hostname);
            Files.stoolFile(pair.privateKey());
            Files.stoolFile(pair.certificate());
        }
        return pair;
    }

    private Pair newPair(String hostname) {
        FileNode crt, key;

        crt = workDir.join(hostname.replace("*", "_") + ".crt");
        key = workDir.join(hostname.replace("*", "_") + ".key");
        return new Pair(key, crt);

    }

    public void generate(String getUrl) throws IOException {
        downloadZip(getUrl).unzip(workDir);
    }

    private FileNode downloadZip(String getUrl) throws IOException {
        StringWriter output;
        FileNode tmp;
        Launcher launcher;
        output = new StringWriter();

        tmp = workDir.getWorld().getTemp().createTempDirectory();
        // TODO: use sushi instead
        launcher = tmp.launcher("wget", "--no-check-certificate", getUrl, "-O", tmp.join("cert.zip").getAbsolute());
        try {
            launcher.exec(output);
            return tmp.join("cert.zip");
        } catch (Failure e) {
            throw new IOException(launcher.toString() + " failed:\n" + e.getMessage() + output.toString(), e.getCause());
        }
    }
}
