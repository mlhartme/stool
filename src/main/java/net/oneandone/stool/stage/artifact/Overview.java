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

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Overview implements ArtifactSource {

    private final String jdkHome;
    private final World world;

    public Overview(String jdkHome, World world) {
        this.jdkHome = jdkHome;
        this.world = world;
    }
    @Override
    public WarFile resolve() throws IOException {
        FileNode tmp, lib, cp, dest;

        tmp = world.getTemp().createTempDirectory();
        dest = world.getTemp().createTempDirectory().createTempFile();

        world.resource("templates/overview").copyDirectory(tmp);
        lib = tmp.join("WEB-INF/lib").mkdir();

        cp = world.locateClasspathItem(getClass());
        if (cp.isDirectory()) {
            // running tests from classes directory; locate stool jar instead
            cp = world.guessProjectHome(getClass()).join("target/setup-stool");
        }

        cp.copyFile(lib.join("stool.jar"));

        tmp.exec(jdkHome + "/bin/jar", "cf", dest.getAbsolute(), ".");
        return new WarFile(dest);
    }
}
