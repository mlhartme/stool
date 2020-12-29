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
package net.oneandone.stool.values;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Configuration;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.core.Stage;
import net.oneandone.stool.server.settings.Expire;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
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
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

public class Expressions {
    private final World world;
    private final Configuration configuration;
    private final TagInfo image;
    private final String fqdn;

    public Expressions(World world, Configuration configuration, TagInfo image, String fqdn) {
        this.world = world;
        this.configuration = configuration;
        this.image = image;
        this.fqdn = fqdn;
    }

    public String eval(String expr) throws IOException {
        int start;
        int end;

        start = expr.indexOf("{{");
        if (start < 0) {
            return expr;
        }
        end = expr.lastIndexOf("}}");
        if (end < 0) {
            throw new IOException("missing closing brackets: " + expr);
        }
        return expr.substring(0, start) + macro(expr.substring(start + 2, end)) + expr.substring(end + 2);
    }

    private String macro(String macro) throws IOException {
        int idx;
        List<String> call;

        macro = macro.trim();
        // TODO: just 1 arg ...
        idx = macro.indexOf(' ');
        if (idx < 0) {
            call = Strings.toList(macro);
        } else {
            call = Strings.toList(macro.substring(0, idx), macro.substring(idx + 1));
        }
        switch (call.get(0)) {
            case "defaultExpire":
                arg(call, 0);
                return Expire.fromNumber(configuration.defaultExpire).toString();
            case "defaultContact":
                arg(call, 0);
                return Stage.NOTIFY_FIRST_MODIFIER;
            case "label":
                arg(call, 1);
                return label(eval(call.get(1)));
            case "image":
                arg(call, 0);
                return image.repositoryTag;
            case "fqdn":
                arg(call, 0);
                return fqdn;
            case "cert":
                arg(call, 0);
                return cert();
            case "fault":
                arg(call, 1);
                return fault(Separator.COMMA.split(eval(call.get(1))));
            default:
                throw new IOException("unknown macro: " + macro);
        }
    }

    private void arg(List<String> call, int size) throws IOException {
        if (call.size() - 1 != size) {
            throw new IOException(call.get(0) + ": argument count mismatch, expected " + size + ", got " + (call.size() - 1));
        }
    }

    private String label(String name) throws IOException {
        String result;

        result = image.labels.get(name);
        if (result == null) {
            throw new IOException("label not found: " + name);
        }
        return result;
    }

    private String cert() throws IOException {
        FileNode dir;

        dir = configuration.certificates().generate(fqdn);
        return Base64.getEncoder().encodeToString(dir.join("keystore.p12").readBytes());
    }


    /** tar directory into byte array */
    private String fault(List<String> faultProjects) throws IOException {
        List<String> missing;
        FileNode workspace;
        FileNode project;
        TarOutputStream tar;
        byte[] buffer;
        long now;
        String result;

        missing = new ArrayList<>();
        if (configuration.auth()) {
            checkFaultPermissions(world, image.author, faultProjects);
        }
        workspace = world.file("/etc/fault/workspace");
        buffer = new byte[64 * 1024];
        try (ByteArrayOutputStream dest = new ByteArrayOutputStream()) {
            tar = new TarOutputStream(new GZIPOutputStream(dest));
            now = System.currentTimeMillis();
            for (String projectName : faultProjects) {
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

    //--

    public static void checkFaultPermissions(World world, String user, List<String> projects) throws IOException {
        Properties permissions;
        String lst;

        if (projects.isEmpty()) {
            return;
        }
        permissions = world.file("/etc/fault/workspace.permissions").readProperties();
        for (String project : projects) {
            lst = permissions.getProperty(project);
            if (lst == null) {
                throw new ArgumentException("fault project unknown or not accessible on this host: " + project);
            }
            if (!Separator.COMMA.split(lst).contains(user)) {
                throw new ArgumentException("fault project " + project + ": permission denied for user " + user);
            }
        }
    }
}
