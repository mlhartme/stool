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
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpNode;

import java.io.IOException;

/** Pair of key/cert pem files.  */
public class Pair {
    public static final String HOSTNAME = "itca.server.lan";
    public static final String URL_PREFIX = "https://" + HOSTNAME + "/cgi-bin/cert.cgi?action=create%20certificate&cert-commonName=";

    public static Pair create(String scriptOrUrl, String hostname, FileNode workDir) throws IOException {
        Pair pair;

        if (scriptOrUrl.isEmpty()) {
            pair = selfSigned(workDir, hostname);
        } else if (scriptOrUrl.equals(URL_PREFIX)) {
            pair = itca(workDir, hostname);
        } else if (scriptOrUrl.startsWith("http://") || scriptOrUrl.startsWith("https://")) {
            pair = fromCsr(workDir, scriptOrUrl, hostname);
        } else {
            pair = fromScript(workDir.getWorld().file(scriptOrUrl), workDir, hostname);
        }
        for (FileNode file : workDir.list()) {
            Files.stoolFile(file);
        }
        return pair;
    }

    public static Pair fromScript(FileNode script, FileNode workDir, String hostname) throws IOException {
        FileNode cert;
        FileNode key;
        String log;

        cert = workDir.join("cert.pem");
        key = workDir.join("key.pem");
        log = workDir.exec(script.getAbsolute(), hostname, key.getAbsolute(), cert.getAbsolute());
        workDir.join("log").writeString(log);
        return new Pair(key, cert);
    }

    public static Pair selfSigned(FileNode workDir, String hostname) throws IOException {
        FileNode cert;
        FileNode key;

        cert = workDir.join("cert.pem");
        key = workDir.join("key.pem");
        workDir.exec("openssl", "req", "-x509", "-newkey", "rsa:2048", "-keyout", key.getAbsolute(), "-out", cert.getAbsolute(),
                "-days", "365", "-nodes", "-subj", "/CN=" + hostname);
        return new Pair(key, cert);
    }

    public static Pair itca(FileNode workDir, String hostname) throws IOException {
        FileNode crt, key;
        Pair pair;
        FileNode zip;
        World world;
        HttpNode itca;

        crt = workDir.join(hostname.replace("*", "_") + ".crt");
        key = workDir.join(hostname.replace("*", "_") + ".key");
        pair = new Pair(key, crt);
        if (!(pair.privateKey().exists() || pair.certificate().exists())) {
            world = workDir.getWorld();
            zip = world.getTemp().createTempFile();
            itca = (HttpNode) world.validNode(URL_PREFIX + hostname);
            itca.copyFile(zip);
            zip.unzip(workDir);
        }
        return pair;
    }

    public static Pair fromCsr(FileNode workDir, String url, String hostname) throws IOException {
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

    //--

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
