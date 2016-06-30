package net.oneandone.stool.ssl;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.HttpNode;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

public class Itca {
    public static final String URL_PREFIX = "https://itca.server.lan/cgi-bin/cert.cgi?action=create%20certificate&cert-commonName=";

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
        try {
            disableCertificates((HttpFilesystem) world.getFilesystem("https"));
        } catch (KeyManagementException |NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        zip = world.getTemp().createTempFile();
        itca = (HttpNode) world.validNode(url);
        bytes = itca.readBytes();
        zip.writeBytes(bytes);
        zip.unzip(workDir);
    }

    public static void disableCertificates(HttpFilesystem dest) throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        dest.setSocketFactorySelector((protocol, hostname) -> protocol.equals("https") ? sc.getSocketFactory() : null );
    }
}
