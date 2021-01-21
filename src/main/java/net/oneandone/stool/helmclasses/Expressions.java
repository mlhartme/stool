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
package net.oneandone.stool.helmclasses;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Configuration;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.core.Stage;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

public class Expressions {
    public final World world;
    public final Configuration configuration;
    private final String fqdn;

    /** value is
     *   string when ready
     *   valuetype when to do
     *   null when recursion started
     */
    private Map<String, Object> context;

    public Expressions(World world, Configuration configuration, String fqdn) {
        this.world = world;
        this.configuration = configuration;
        this.fqdn = fqdn;
        this.context = null;
    }

    public Map<String, String> eval(Clazz clazz) {
        Map<String, String> result;

        if (context != null) {
            throw new IllegalStateException();
        }
        context = new HashMap<>();
        try {
            for (ValueType type : clazz.values.values()) {
                context.put(type.name, type);
            }
            for (ValueType type : clazz.values.values()) {
                evalValue(type.name);
            }
            result = new HashMap<>();
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                result.put(entry.getKey(), (String) entry.getValue());
            }
            return result;
        } finally {
            context = null;
        }
    }

    private String evalValue(String name) {
        Object obj;
        String result;

        if (!context.containsKey(name)) {
            throw new ArgumentException("unknown field: " + name);
        }
        obj = context.get(name);
        if (obj == null) {
            throw new ArgumentException("invalid recursion on field " + name);
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        if (obj instanceof ValueType) {
            context.put(name, null);
            try {
                result = eval(((ValueType) obj).value);
            } catch (IOException e) {
                throw new ArgumentException("failed to compute value: " + name, e);
            }
            context.put(name, result);
            return result;
        }
        throw new IllegalStateException(obj.getClass().toString());
    }

    public String eval(String str) throws IOException {
        freemarker.template.Configuration fm;
        FileNode srcfile;
        FileNode destfile;
        Template template;
        StringWriter tmp;

        fm = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_26);
        fm.setDefaultEncoding("UTF-8");

        srcfile = world.getTemp().createTempFile();
        srcfile.writeString(str);
        destfile = world.getTemp().createTempFile();
        try {
            fm.setDirectoryForTemplateLoading(srcfile.getParent().toPath().toFile());
            template = fm.getTemplate(srcfile.getName());
            tmp = new StringWriter();
            template.process(templateEnv(), tmp);
            destfile.writeString(tmp.getBuffer().toString());
            return destfile.readString();
        } catch (TemplateException e) {
            throw new IOException("freemarker error: " + e.getMessage(), e);
        } finally {
            destfile.deleteFile();
            srcfile.deleteFile();
        }
    }

    private Map<String, Object> templateEnv() {
        Map<String, Object> result;

        result = new HashMap<>();
        result.put("fqdn", fqdn);
        result.put("defaultExpire", configuration.defaultExpire);
        result.put("defaultContact", Stage.NOTIFY_FIRST_MODIFIER);
        result.put("latest", new TemplateMethodModelEx() {
            @Override
            public Object exec(List list) throws TemplateModelException {
                if (list.size() != 1) {
                    throw new ArgumentException(list.toString());
                }
                try {
                    return latest(list.get(0).toString());
                } catch (IOException e) {
                    throw new TemplateModelException(e.getMessage(), e);
                }
            }
        });
        result.put("label", new TemplateMethodModelEx() {
            @Override
            public Object exec(List list) throws TemplateModelException {
                if (list.size() != 2) {
                    throw new ArgumentException(list.toString());
                }
                try {
                    return label(list.get(0).toString(), list.get(1).toString());
                } catch (IOException e) {
                    throw new TemplateModelException(e.getMessage(), e);
                }
            }
        });
        result.put("labelOpt", new TemplateMethodModelEx() {
            @Override
            public Object exec(List list) throws TemplateModelException {
                if (list.size() != 2) {
                    throw new ArgumentException(list.toString());
                }
                try {
                    return labelOpt(list.get(0).toString(), list.get(1).toString());
                } catch (IOException e) {
                    throw new TemplateModelException(e.getMessage(), e);
                }
            }
        });
        result.put("cert", new TemplateMethodModelEx() {
            @Override
            public Object exec(List list) throws TemplateModelException {
                if (list.size() != 0) {
                    throw new ArgumentException(list.toString());
                }
                try {
                    return cert();
                } catch (IOException e) {
                    throw new TemplateModelException(e.getMessage(), e);
                }
            }
        });
        result.put("fault", new TemplateMethodModelEx() {
            @Override
            public Object exec(List list) throws TemplateModelException {
                if (list.size() != 1) {
                    throw new ArgumentException(list.toString());
                }
                try {
                    return fault(Separator.COMMA.split(list.get(0).toString()));
                } catch (IOException e) {
                    throw new TemplateModelException(e.getMessage(), e);
                }
            }
        });
        result.put("value", new TemplateMethodModelEx() {
            @Override
            public Object exec(List list) throws TemplateModelException {
                if (list.size() != 1) {
                    throw new ArgumentException(list.toString());
                }
                return evalValue(list.get(0).toString());
            }
        });
        return result;
    }

    private String label(String imageOrRepository, String label) throws IOException, TemplateModelException {
        String result;

        result = labelOpt(imageOrRepository, label);
        if (result == null) {
            throw new TemplateModelException("label not found: " + label);
        }
        return result;
    }

    private String labelOpt(String imageOrRepository, String label) throws IOException {
        Registry registry;
        String result;

        Helm.validateRepository(Registry.toRepository(imageOrRepository));
        registry = configuration.createRegistry(world, imageOrRepository);
        result = registry.resolve(imageOrRepository).labels.get(label);
        return result == null ? "" : result;
    }

    public String latest(String imageOrRepository) throws IOException {
        Registry registry;

        Helm.validateRepository(Registry.toRepository(imageOrRepository));
        registry = configuration.createRegistry(world, imageOrRepository);
        return registry.resolve(imageOrRepository).repositoryTag;
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
            // TODO:
            // checkFaultPermissions(world, image.author, faultProjects);
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
