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
package net.oneandone.stool.docker;

import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import javax.net.SocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

public class DaemonSocket {
    public static void main(String[] args) throws Exception {
        URL url;
        HttpsURLConnection conn;

        HttpsURLConnection.setDefaultSSLSocketFactory(tcpSocketFactory(World.create().file("/Users/mhm/.minishift/certs")));

        url = new URL("https://192.168.64.7:2376/v1.40/version");
        conn = (HttpsURLConnection) url.openConnection();
        try (InputStream is = conn.getInputStream()) {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            String inputLine;

            while ((inputLine = br.readLine()) != null) {
                System.out.println(inputLine);
            }
        }
    }

    public static SSLSocketFactory tcpSocketFactory(FileNode certs) {
        KeyStore trustStore;
        KeyStore keyStore;
        SSLContext sslContext;

        try {
            trustStore = trustStore(certs.join("ca.pem"));
            keyStore = keyStore(certs);
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(new KeyManager[]{keyManager(keyStore)}, new TrustManager[]{trustManager(trustStore)}, null);
        } catch (Exception e) {
            throw new IllegalStateException("TODO: " + e.getMessage(), e);
        }
        return sslContext.getSocketFactory();
    }

    //--  see https://gist.github.com/dain/29ce5c135796c007f9ec88e82ab21822

    private static final Pattern CERT_PATTERN = Pattern.compile(
            "-+BEGIN\\s+.*CERTIFICATE[^-]*-+(?:\\s|\\r|\\n)+" + // Header
                    "([a-z0-9+/=\\r\\n]+)" +                    // Base64 text
                    "-+END\\s+.*CERTIFICATE[^-]*-+",            // Footer
            CASE_INSENSITIVE);

    private static final Pattern KEY_PATTERN = Pattern.compile(
            "-+BEGIN\\s+.*PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" + // Header
                    "([a-z0-9+/=\\r\\n]+)" +                       // Base64 text
                    "-+END\\s+.*PRIVATE\\s+KEY[^-]*-+",            // Footer
            CASE_INSENSITIVE);

    public static KeyStore loadTrustStore(String certificateChainArg) throws IOException, GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);

        List<X509Certificate> certificateChain = readCertificateChain(certificateChainArg);
        for (X509Certificate certificate : certificateChain) {
            X500Principal principal = certificate.getSubjectX500Principal();
            keyStore.setCertificateEntry(principal.getName("RFC2253"), certificate);
        }
        return keyStore;
    }

    public static KeyStore loadKeyStore(String certificateChainArg, String privateKey) throws IOException, GeneralSecurityException {
        PKCS8EncodedKeySpec encodedKeySpec = readPrivateKey(privateKey);
        PrivateKey key;

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        key = keyFactory.generatePrivate(encodedKeySpec);

        List<X509Certificate> certificateChain = readCertificateChain(certificateChainArg);
        if (certificateChain.isEmpty()) {
            throw new CertificateException("Certificate file does not contain any certificates");
        }

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        keyStore.setKeyEntry("key", key, "".toCharArray(), certificateChain.stream().toArray(Certificate[]::new));
        return keyStore;
    }

    //--

    private static List<X509Certificate> readCertificateChain(String contents) throws GeneralSecurityException {
        Matcher matcher = CERT_PATTERN.matcher(contents);
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certificates = new ArrayList<>();

        int start = 0;
        while (matcher.find(start)) {
            byte[] buffer = decode(matcher.group(1));
            certificates.add((X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(buffer)));
            start = matcher.end();
        }

        return certificates;
    }

    private static PKCS8EncodedKeySpec readPrivateKey(String content) throws IOException {
        Matcher matcher = KEY_PATTERN.matcher(content);
        if (!matcher.find()) {
            throw new IOException("found no private key");
        }
        byte[] encodedKey = decode(matcher.group(1));

        return new PKCS8EncodedKeySpec(encodedKey);
    }

    private static KeyStore keyStore(FileNode dir) throws Exception {
        return loadKeyStore(dir.join("cert.pem").readString(), dir.join("key8.pem").readString());
    }

    private static KeyStore trustStore(FileNode ca) throws GeneralSecurityException, IOException {
        KeyStore result;

        result = loadTrustStore(ca.readString());
        /*
        Enumeration e = result.aliases();
        while (e.hasMoreElements()) {
            System.out.println("alias: " + e.nextElement());
        }*/
        return result;
    }

    private static X509TrustManager trustManager(KeyStore trustStore) throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory trustManagerFactory;

        trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
        trustManagerFactory.init(trustStore);
        for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }
        throw new IllegalStateException();
    }

    private static X509KeyManager keyManager(KeyStore keyStore) throws NoSuchAlgorithmException, KeyStoreException, NoSuchProviderException, UnrecoverableKeyException {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509", "SunJSSE");
        keyManagerFactory.init(keyStore, "".toCharArray());

        for (KeyManager keyManager : keyManagerFactory.getKeyManagers()) {
            if (keyManager instanceof X509KeyManager) {
                return (X509KeyManager) keyManager;
            }
        }
        throw new IllegalStateException();
    }

    private static byte[] decode(String base64) {
        return Base64.getMimeDecoder().decode(base64.getBytes(US_ASCII));
    }


    public static SocketFactory unixSocketFactory(String path) {
        return new SocketFactory() {
            @Override
            public Socket createSocket(String s, int i) throws IOException {
                return socket();
            }

            @Override
            public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException {
                return socket();
            }

            @Override
            public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
                return socket();
            }

            @Override
            public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
                return socket();
            }

            private Socket socket() throws IOException {
                UnixSocketAddress address;

                address = new UnixSocketAddress(new File(path));
                return UnixSocketChannel.open(address).socket();
            }
        };
    }

    private DaemonSocket() {
    }
}
