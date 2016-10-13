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
import net.oneandone.sushi.launcher.Failure;

import java.io.IOException;
import java.io.Writer;

/** Pair of key/cert pem files.  */
public class Pair {
    public static Pair create(String scriptOrUrl, String hostname, FileNode workDir) throws IOException {
        Pair pair;

        if (scriptOrUrl.isEmpty()) {
            pair = selfSigned(workDir, hostname);
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

        cert = workDir.join("cert.pem");
        key = workDir.join("key.pem");
        script.checkFile();
        try (Writer log  = workDir.join("log").newAppender()) {
            workDir.launcher(script.getAbsolute(), hostname).exec(log);
        } catch (Failure e) {
            throw new IOException(script + " failed - check log file in directory " + workDir);
        }
        return new Pair(key, cert, true);
    }

    public static Pair selfSigned(FileNode workDir, String hostname) throws IOException {
        FileNode cert;
        FileNode key;

        cert = workDir.join("cert.pem");
        key = workDir.join("key.pem");
        workDir.exec("openssl", "req", "-x509", "-newkey", "rsa:2048", "-keyout", key.getAbsolute(), "-out", cert.getAbsolute(),
                "-days", "365", "-nodes", "-subj", "/CN=" + hostname);
        return new Pair(key, cert, false);
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
        return new Pair(key, cert, false);
    }

    //--

    private final FileNode privateKey;
    private final FileNode certificate;
    private final boolean script;

    public Pair(FileNode privateKey, FileNode certificate, boolean script) {
        this.privateKey = privateKey;
        this.certificate = certificate;
        this.script = script;
    }

    public FileNode privateKey() {
        return privateKey;
    }

    public FileNode certificate() {
        return certificate;
    }

    public boolean isScript() {
        return script;
    }

    public String text() throws IOException {
        return privateKey.readString() + "\n" + certificate.readString();
    }
}
