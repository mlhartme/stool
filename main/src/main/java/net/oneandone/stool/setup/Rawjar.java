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
package net.oneandone.stool.setup;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

/** Generates bin- and man directories. Used at build time (for Debian Package) or runtime (Java Installer) */
public class Rawjar {
    /** generate file hierarchie for debian installer */
    public static void main(String[] args) throws Exception {
        World world;
        FileNode src;
        FileNode dest;
        byte[] bytes;
        int idx;

        if (args.length != 2) {
            throw new IllegalArgumentException();
        }
        world = new World();
        src = world.file(args[0]);
        dest = world.file(args[1]);
        bytes = src.readBytes();
        idx = BinMan.indexOf(bytes, new byte[] {'\n', 'P', 'K' });
        idx++;
        System.out.println("idx: " + idx);
        dest.writeBytes(bytes, idx, bytes.length - idx, false);
    }
}
