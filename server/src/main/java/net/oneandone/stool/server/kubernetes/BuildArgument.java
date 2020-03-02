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
package net.oneandone.stool.server.kubernetes;

import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BuildArgument {
    public static Map<String, BuildArgument> scan(FileNode file) throws IOException {
        Map<String, BuildArgument> result;
        BuildArgument buildArgument;

        result = new HashMap<>();
        if (file.isFile()) {
            for (String line : file.readLines()) {
                buildArgument = scan(line);
                if (buildArgument != null) {
                    if (result.put(buildArgument.name, buildArgument) != null) {
                        throw new IOException("duplicate variable: " + buildArgument.name);
                    }
                }
            }
        }
        return result;
    }

    public static BuildArgument scan(String line) {
        String name;
        String dflt;
        int idx;

        line = line.trim();
        if (line.length() < 4 || !line.substring(0, 3).trim().toUpperCase().equals("ARG")) {
            return null;
        }
        line = line.substring(4).trim();
        idx = line.indexOf('=');
        if (idx == -1) {
            name = line;
            dflt = "";
        } else {
            name = line.substring(0, idx).trim();
            dflt = line.substring(idx + 1).trim();
        }
        return new BuildArgument(name, dflt);
    }

    public final String name;
    public final String dflt;


    public BuildArgument(String name, String dflt) {
        this.name = name;
        this.dflt = dflt;
    }

    public String toString(Object value) {
        return value.toString();
    }
}
