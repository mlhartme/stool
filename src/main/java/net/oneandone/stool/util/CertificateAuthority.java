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
import java.io.StringWriter;

public class CertificateAuthority {
    private final String hostname;
    private final FileNode workingDir;


    public CertificateAuthority(FileNode workingDir, String hostname) {
        this.workingDir = workingDir;
        this.hostname = hostname;
    }


    public Certificate certificate() throws IOException {
        Certificate certificate;
        certificate = create();
        if (!(certificate.privateKey().exists() || certificate.certificate().exists())) {
            sign();
            Files.stoolFile(certificate.privateKey());
            Files.stoolFile(certificate.certificate());
        }
        return certificate;
    }

    private Certificate create() throws IOException {
        FileNode crt, key;

        crt = workingDir.join(hostname.replace("*", "_") + ".crt");
        key = workingDir.join(hostname.replace("*", "_") + ".key");
        return new Certificate(key, crt);

    }

    public void sign() throws IOException {
        extract(download());
    }

    private FileNode download() throws IOException {
        String base;
        StringWriter output;
        output = new StringWriter();
        base = "https://itca.server.lan/cgi-bin/cert.cgi?action=create%20certificate&cert-commonName=";
        try {
            FileNode tmp;
            tmp = workingDir.getWorld().getTemp().createTempDirectory();
            tmp.launcher("wget", "--no-check-certificate", base + hostname, "-O", tmp.join("cert.zip")
              .getAbsolute()).exec(output);
            return tmp.join("cert.zip");
        } catch (Failure e) {
            throw new IOException(e.getMessage() + output.toString(), e.getCause());
        }
    }

    private void extract(FileNode certificateZip) throws IOException {
        certificateZip.unzip(workingDir);
    }

}
