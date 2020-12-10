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
package net.oneandone.stool.server;

import net.oneandone.stool.registry.TagInfo;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class App {
    private final World world;
    private final Server server;
    private final TagInfo image;

    public App(World world, Server server, TagInfo image) {
        this.world = world;
        this.server = server;
        this.image = image;
    }

    public void addValues(Stage stage, Map<String, Object> map) throws IOException {
        map.put("image", image.repositoryTag);
        map.put("fqdn", stage.stageFqdn());
        map.put("cert", cert(stage.stageFqdn()));
        map.put("fault", fault());
    }

    private String cert(String fqdn) throws IOException {
        FileNode dir;

        dir = server.certificate(fqdn);
        return Base64.getEncoder().encodeToString(dir.join("keystore.p12").readBytes());
    }


    /** tar directory into byte array */
    private String fault() throws IOException {
        List<String> missing;
        FileNode workspace;
        FileNode project;
        TarOutputStream tar;
        byte[] buffer;
        long now;
        String result;

        missing = new ArrayList<>();
        if (server.settings.auth()) {
            server.checkFaultPermissions(image.author, image.faultProjects);
        }
        workspace = world.file("/etc/fault/workspace");
        buffer = new byte[64 * 1024];
        try (ByteArrayOutputStream dest = new ByteArrayOutputStream()) {
            tar = new TarOutputStream(new GZIPOutputStream(dest));
            now = System.currentTimeMillis();
            for (String projectName : image.faultProjects) {
                project = workspace.join(projectName);
                if (project.isDirectory()) {
                    faultTarAdd(now, buffer, workspace, project, tar);
                } else {
                    missing.add(projectName);
                }
            }
            tar.close();
            result = Base64.getEncoder().encodeToString(dest.toByteArray());
        }
        if (!missing.isEmpty()) {
            throw new ArgumentException("missing secret directories: " + missing);
        }
        return result;
    }

    /** tar directory into byte array */
    private void faultTarAdd(long now, byte[] buffer, FileNode workspace, FileNode project, TarOutputStream tar) throws IOException {
        List<FileNode> all;
        Iterator<FileNode> iter;
        FileNode file;
        int count;

        all = project.find("**/*");
        iter = all.iterator();
        while (iter.hasNext()) {
            file = iter.next();
            if (file.isDirectory()) {
                tar.putNextEntry(new TarEntry(TarHeader.createHeader(file.getRelative(workspace), 0, now, true, 0700)));
                iter.remove();
            }
        }
        iter = all.iterator();
        while (iter.hasNext()) {
            file = iter.next();
            tar.putNextEntry(new TarEntry(TarHeader.createHeader(file.getRelative(workspace), file.size(), now, false, 0700)));
            try (InputStream src = file.newInputStream()) {
                while (true) {
                    count = src.read(buffer);
                    if (count == -1) {
                        break;
                    }
                    tar.write(buffer, 0, count);
                }
            }
        }
    }
}
