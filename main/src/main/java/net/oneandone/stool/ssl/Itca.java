package net.oneandone.stool.ssl;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpNode;

import java.io.IOException;

public class Itca {
    public static final String HOSTNAME = "itca.server.lan";
    public static final String URL_PREFIX = "https://" + HOSTNAME + "/cgi-bin/cert.cgi?action=create%20certificate&cert-commonName=";

    public static Pair create(FileNode workDir, String hostname) throws IOException {
        Pair pair;

        pair = newPair(workDir, hostname);
        if (!(pair.privateKey().exists() || pair.certificate().exists())) {
            download(workDir, URL_PREFIX + hostname);
        }
        return pair;
    }

    private static Pair newPair(FileNode workDir, String hostname) {
        FileNode crt, key;

        crt = workDir.join(hostname.replace("*", "_") + ".crt");
        key = workDir.join(hostname.replace("*", "_") + ".key");
        return new Pair(key, crt);

    }

    public static void download(FileNode workDir, String url) throws IOException {
        FileNode zip;
        World world;
        HttpNode itca;
        byte[] bytes;

        world = workDir.getWorld();
        zip = world.getTemp().createTempFile();
        itca = (HttpNode) world.validNode(url);
        bytes = itca.readBytes();
        zip.writeBytes(bytes);
        zip.unzip(workDir);
    }
}
