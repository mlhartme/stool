/*
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
package net.oneandone.stool.stage.artifact;

import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.Properties;

public class WarFile {
    private final FileNode file;
    private String version;
    private long revision;

    public WarFile(FileNode file) {
        this.file = file;
    }

    public FileNode file() {
        return file;
    }

    public boolean exists() {
        return file.exists();
    }

    public void copyTo(WarFile destination) throws IOException {
        file.copyFile(destination.file);
    }

    public long revision() throws IOException {
        if (revision == 0) {
            readPomInfo();
        }
        return revision;
    }

    public String version() throws IOException {
        if (version == null) {
            readPomInfo();
        }
        return version;
    }

    private void readPomInfo() throws IOException {
        Node pominfo;
        Properties properties;

        // TODO - pominfo plugin is not publically available
        pominfo = file.openZip().join("WEB-INF", "classes", "META-INF", "pominfo.properties");
        if (!pominfo.exists()) {
            throw new IOException("No pominfo.properties available. Parent Pom is too old. Please update.");
        }
        properties = pominfo.readProperties();
        if (properties.get("scmConnection") == null || properties.get("build.revision") == null) {
            throw new IOException("Parent Pom is too old to assemble Changes. Please update.");
        }
        version = properties.getProperty("version");
        revision = Long.parseLong(properties.getProperty("build.revision"));
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        WarFile other;

        if (obj instanceof WarFile) {
            other = (WarFile) obj;
            try {
                return other.exists() && exists() && (file.size() == other.file.size()) && file.md5().equals(other.file.md5());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

}
