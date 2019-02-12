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
package net.oneandone.stool.stage;

import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.scm.Scm;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.filter.Filter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SourceProject extends Project {
    public static SourceProject forOrigin(Session session, String id, FileNode directory, String origin, StageConfiguration configuration) {
        return new SourceProject(session, id, directory, origin, configuration);
    }
    public static SourceProject forLocal(Session session, String id, FileNode stage, StageConfiguration configuration)
            throws IOException {
        return forOrigin(session, id, stage, Scm.checkoutUrl(stage), configuration);
    }

    //--

    public SourceProject(Session session, String id, FileNode directory, String origin, StageConfiguration configuration) {
        super(session, origin, id, directory, configuration);
    }

    @Override
    public List<String> faultProjects() throws IOException {
        List<String> result;

        result = new ArrayList<>();
        for (FileNode war : wars().values()) {
            result.add("file:" + war.getAbsolute());
        }
        return result;
    }

    @Override
    public int size() throws IOException {
        return wars().size();
    }

    @Override
    public Map<String, FileNode> vhosts() throws IOException {
        Map<String, FileNode> applications;

        applications = new LinkedHashMap<>();
        for (Map.Entry<String, FileNode> entry : wars().entrySet()) {
            applications.put(entry.getKey(), docroot(entry.getValue()));
        }
        return applications;
    }

    private FileNode docroot(FileNode war) throws IOException {
        FileNode basedir;
        List<FileNode> result;
        Filter filter;

        basedir = war.getParent().getParent();
        filter = basedir.getWorld().filter();
        filter.include("target/*/WEB-INF");
        filter.predicate((node, b) -> node.isDirectory() && (node.join("lib").isDirectory() || node.join("classes").isDirectory()));
        filter.exclude("target/test-classes/**/*");
        result = basedir.find(filter);
        switch (result.size()) {
            case 0:
                throw new IOException("No web application found. Did you build the project?");
            case 1:
                return result.get(0).getParent();
            default:
                throw new FileNotFoundException("web.xml ambiguous: " + result);
        }
    }
}

