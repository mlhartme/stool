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
package net.oneandone.stool.core;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Certificates {
    private static final Logger LOGGER = LoggerFactory.getLogger(Certificates.class);

    private final FileNode base;
    private final String fqdn;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public Certificates(FileNode base, String fqdn) {
        this.base = base;
        this.fqdn = fqdn;
    }

    /** @return path to generates directory */
    public FileNode generate(String certname) throws IOException {
        World world;
        FileNode script;
        FileNode dir;
        FileNode broken;

        world = base.getWorld();
        script = base.join("cert.sh");
        if (!script.isFile()) {
            world.resource("cert-selfsigned.sh").copyFile(script);
            script.setPermissions("rwxr-xr-x");
        }
        base.join("certs").mkdirsOpt();
        dir = base.join("certs", certname);
        try {
            LOGGER.info(world.getTemp().exec(script.getAbsolute(), certname, dir.getAbsolute(), fqdn));
        } catch (IOException e) {
            LOGGER.error(script.getAbsolute() + " failed: " + e.getMessage(), e);
            broken = dir.getParent().join(dir.getName() + ".broken");
            broken.deleteTreeOpt();
            dir.move(broken);
            broken.join("error.log").writeString(e.getMessage());
            throw e;
        }
        return dir;
    }
}
