package net.oneandone.stool.helmclasses;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class Freemarker {
    private final World world;

    public Freemarker(World world) {
        this.world = world;

    }
    public String compute(String str) throws IOException, TemplateException {
        Configuration configuration;
        FileNode srcfile;
        FileNode destfile;
        Template template;
        StringWriter tmp;

        configuration = new Configuration(Configuration.VERSION_2_3_26);
        configuration.setDefaultEncoding("UTF-8");

        srcfile = world.getTemp().createTempFile();
        srcfile.writeString(str);
        destfile = world.getTemp().createTempFile();
        try {
            configuration.setDirectoryForTemplateLoading(srcfile.getParent().toPath().toFile());
            template = configuration.getTemplate(srcfile.getName());
            tmp = new StringWriter();
            template.process(templateEnv(), tmp);
            destfile.writeString(tmp.getBuffer().toString());
            return destfile.readString();
        } finally {
            destfile.deleteFile();
            srcfile.deleteFile();
        }
    }

    private Map<String, Object> templateEnv() throws IOException {
        Map<String, Object> result;

        result = new HashMap<>();
        result.put("test", 42);
        return result;
    }


}
