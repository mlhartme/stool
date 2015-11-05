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

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;

import java.io.IOException;
import java.io.PrintWriter;

public class Util {
    //TODO: work-around for sushi http problem with proxies
    // TODO: race condition for simultaneous downloads by different users
    public static void downloadFile(PrintWriter log, String url, FileNode dest) throws IOException {
        if (OS.CURRENT != OS.MAC) {
            // don't use sushi, it's not proxy-aware
            dest.getParent().exec("wget", "--tries=1", "--connect-timeout=5", "-q", "-O", dest.getName(), url);
        } else {
            // wget not available on Mac, but Mac usually have no proxy
            dest.getWorld().validNode(url).copyFile(dest);
        }
    }
}
