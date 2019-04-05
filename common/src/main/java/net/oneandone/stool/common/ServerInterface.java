package net.oneandone.stool.common;

import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ServerInterface {
    List<Reference> search(String stageClause, Map<String, IOException> problems) throws IOException;
    Reference create(String name, Map<String, String> config) throws IOException;

    BuildResult build(Reference reference, String app, FileNode war, String comment,
                      String origin, String createdBy, String createdOn, boolean noCache, int keep,
                      Map<String, String> arguments) throws Exception;

    void start(Reference reference, int http, int https, Map<String, String> startEnvironment, Map<String, Integer> apps) throws IOException;

    Map<String, List<String>> awaitStartup(Reference reference) throws IOException, InterruptedException;

    void stop(Reference reference, List<String> apps) throws IOException;

    void remove(Reference reference) throws IOException;

    Map<String, String> status(Reference reference, List<String> selected) throws IOException;

    List<String> history(Reference reference, boolean details, int max) throws IOException;

    List<String> apps(Reference reference) throws IOException;

    //-- validate

    List<String> validate(String stageClause, boolean email, boolean repair) throws Exception;

    //-- config command

    Map<String, String> getProperties(Reference reference) throws Exception;

    Map<String, String> setProperties(Reference reference, Map<String, String> arguments) throws IOException;

    //-- app info

    List<String> appInfo(Reference reference, String app) throws Exception;

}
