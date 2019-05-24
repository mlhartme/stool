package net.oneandone.stool.server.api;

import net.oneandone.sushi.fs.World;
import org.apache.tomcat.util.codec.binary.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Based on https://stackoverflow.com/questions/3706177/how-to-generate-ssh-compatible-id-rsa-pub-from-java
 * and https://www.programcreek.com/java-api-examples/?class=java.security.PrivateKey&method=getEncoded
 */
public class Tunnel {
    public static void main(String[] args) throws NoSuchAlgorithmException, IOException {
        KeyPairGenerator generator;
        KeyPair pair;
        World world;

        generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        System.out.println("generating ...");
        pair = generator.genKeyPair();
        world = World.create();
        world.file("id_rsa.pub").writeString(encodePublicRsaKey((RSAPublicKey) pair.getPublic(), "stool-tunnel"));
        world.file("id_rsa").writeString(encodePrivateRsaKey((RSAPrivateKey) pair.getPrivate()));
        System.out.println("done");
    }


    public static String encodePublicRsaKey(RSAPublicKey rsaPublicKey, String user) {
        ByteArrayOutputStream dest;

        dest = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(dest)) {
            data.writeInt("ssh-rsa".getBytes().length);
            data.write("ssh-rsa".getBytes());
            data.writeInt(rsaPublicKey.getPublicExponent().toByteArray().length);
            data.write(rsaPublicKey.getPublicExponent().toByteArray());
            data.writeInt(rsaPublicKey.getModulus().toByteArray().length);
            data.write(rsaPublicKey.getModulus().toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("ioeception on memory stream!?", e);
        }
        return "ssh-rsa " + Base64.encodeBase64String(dest.toByteArray()) + " " + user + "\n";
    }

    public static String encodePrivateRsaKey(RSAPrivateKey privateKey) {
        StringBuilder result;
        byte[] bytes;
        int width;

        bytes = Base64.encodeBase64(privateKey.getEncoded());
        result = new StringBuilder();
        result.append("-----BEGIN RSA PRIVATE KEY-----\n");
        width = 0;
        for (int i = 0; i < bytes.length; i++) {
            result.append((char) bytes[i]);
            width++;
            if (width == 64) {
                result.append('\n');
                width = 0;
            }
        }
        result.append("\n-----END RSA PRIVATE KEY-----\n");
        return result.toString();
    }
}
