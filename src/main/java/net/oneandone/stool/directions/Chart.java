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
package net.oneandone.stool.directions;

import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.Collection;

public class Chart {
    public static Chart get(Collection<Library> libraries, String name) throws IOException {
        Chart result;
        Chart chart;

        result = null;
        for (Library library : libraries) {
            chart = library.lookupChart(name);
            if (chart != null) {
                if (result != null) {
                    throw new IOException("chart ambiguous: " + name);
                }
            }
        }
        if (result == null) {
            throw new IOException("chart not found: " + name);
        }
        return result;
    }

    //--

    public final FileNode directory;
    public final String version;

    public Chart(FileNode directory, String version) {
        this.directory = directory;
        this.version = version;
    }

    public String getName() {
        return directory.getName();
    }
}
