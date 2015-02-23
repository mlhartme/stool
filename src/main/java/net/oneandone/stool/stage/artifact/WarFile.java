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
package net.oneandone.stool.stage.artifact;

import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.MoveException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.Properties;
public class WarFile {
    private final FileNode file;
    private String md5;
    private String svnurl;
    private String version;
    private long revision;

    public WarFile(FileNode file) {
        this.file = file;
    }

    public boolean exists() {
        return file.exists();
    }

    public WarFile relocateTo(FileNode destination) throws MoveException, MkdirException {
        if (!destination.getParent().exists()) {
            destination.getParent().mkdirs();
        }
        return new WarFile((FileNode) file.move(destination, true));
    }

    private String md5() {
        try {
            if (null == md5) {
                md5 = file.md5();
            }
            return md5;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public long revision() throws IOException {
        if (0L != revision) {
            return revision;
        }
        readPomInfo();
        return revision;
    }

    public String version() throws IOException {
        if (null != version) {
            return version;
        }
        readPomInfo();
        return version;
    }

    public void readPomInfo() throws IOException {
        Node pominfo;

        // TODO - pominfo plugin is not publically available
        pominfo = file.openZip().join("WEB-INF", "classes", "META-INF", "pominfo.properties");
        if (!pominfo.exists()) {
            throw new NoChangesAvailableException("No pominfo.properties available. Parent Pom is too old. Please update.");
        }
        Properties properties;
        properties = pominfo.readProperties();
        if (properties.get("scmConnection") == null || properties.get("build.revision") == null) {
            throw new NoChangesAvailableException("Parent Pom is too old to assemble Changes. Please update.");
        }
        svnurl = String.valueOf(properties.get("scmConnection"));
        version = String.valueOf(properties.get("version"));
        revision = Long.valueOf(String.valueOf(properties.get("build.revision")));
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj) {
            return false;
        }
        if (!(obj instanceof WarFile)) {
            return false;
        }
        WarFile other = (WarFile) obj;

        return (other.exists() && exists()) && md5().equals(other.md5());

    }
}
