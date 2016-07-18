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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/** Creates an directory with debian maintainer scripts. */
public class Control {
    public static void main(String[] args) throws IOException {
        World world;

        if (args.length != 3) {
            throw new IOException("usage: control srcdir application dest");
        }
        world = World.create();
        run(world.file(args[0]), world.file(args[1]), world.file(args[2]));
        System.exit(0);
    }

    public static void run(FileNode srcdir, FileNode application, FileNode dest) throws IOException {
        byte[] launcher;
        byte[] orig;
        int idx;

        launcher = srcdir.getWorld().resource("templates/maintainer-launcher").readBytes();

        srcdir.checkDirectory();
        application.checkFile();
        dest.mkdir();

        orig = application.readBytes();
        idx = endOfLauncher(orig);
        srcdir.copyDirectory(dest);
        dest.join("preinst").writeBytes(launcher);
        dest.join("postinst").writeBytes(launcher);
        dest.join("prerm").writeBytes(launcher);
        try (OutputStream out = dest.join("postrm.bin").newOutputStream()) {
            out.write(launcher);
            out.write(orig, idx, orig.length - idx);
        }
    }

    private static int endOfLauncher(byte[] all) {
        try {
            return indexOf(all, "\nPK".getBytes("ascii"));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException();
        }
    }

    public static int indexOf(byte[] array, byte[] sub) {
        int j;

        for (int i = 0; i < array.length - sub.length; i++) {
            for (j = 0; j < sub.length; j++) {
                if (sub[j] != array[i + j]) {
                    break;
                }
            }
            if (j == sub.length) {
                return i;
            }
        }
        throw new IllegalStateException();
    }
}
