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

import net.oneandone.sushi.fs.Copy;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.filter.Filter;
import net.oneandone.sushi.util.Substitution;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class Files {
    public static Node executable(Node file) throws IOException {
        String old;
        StringBuilder next;

        old = file.getPermissions();
        next = new StringBuilder(old);
        next.setCharAt(2, 'x');
        next.setCharAt(5, 'x');
        next.setCharAt(8, 'x');
        if (!old.equals(next)) {
            file.setPermissions(next.toString());
        }
        return file;
    }

    //-- templates


    /** files without keyword substitution */
    private static final String[] BINARY_EXTENSIONS = {".keystore", ".war", ".jar", ".gif", ".ico", ".class "};

    private static Filter withoutBinary(Filter orig) {
        Filter result;

        result = new Filter(orig);
        for (String ext : BINARY_EXTENSIONS) {
            result.exclude("**/*" + ext);
        }
        return result;
    }


    //--

    public static final Substitution S = new Substitution("${{", "}}", '\\');

    public static void template(Node<?> src, FileNode dest, Map<String, String> variables) throws IOException {
        Filter selection;
        List<Node> nodes;

        dest.checkDirectory();
        // Permissions:
        //
        // template files are stool files, but some of the directories contain application files
        // (e.g. tomcat/conf/Catalina). So we cannot remove the whole directory to create a fresh copy
        // (also, we would loose log files by wiping the template) and we cannot simply update permissions
        // on all files (via backstageTree), we have to iterate the file actually part of the template.
        selection = src.getWorld().filter().includeAll();
        nodes = new Copy(src, withoutBinary(selection), false, variables, S).directory(dest);
        for (Node node : nodes) {
            if (!node.isDirectory()) {
                if (node.getName().endsWith(".sh")) {
                    executable(node);
                }
            }
        }
    }

    //--

    private Files() {
    }

}
