package net.oneandone.stool.server.api;

import net.oneandone.sushi.fs.World;
import org.apache.tomcat.util.codec.binary.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;

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
        world.file("id_rsa.pub").writeString(encodePublicRsaKey(pair.getPublic(), "stool-tunnel"));
        world.file("id_rsa").writeString(encodePrivateRsaKey(pair.getPrivate()));
        System.out.println("done");
    }


    public static String encodePublicRsaKey(PublicKey publicKey, String user)
            throws IOException {
        String publicKeyEncoded;

        if(!publicKey.getAlgorithm().equals("RSA")) {
            throw new IllegalStateException();
        }
        RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
        ByteArrayOutputStream byteOs = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(byteOs);
        dos.writeInt("ssh-rsa".getBytes().length);
        dos.write("ssh-rsa".getBytes());
        dos.writeInt(rsaPublicKey.getPublicExponent().toByteArray().length);
        dos.write(rsaPublicKey.getPublicExponent().toByteArray());
        dos.writeInt(rsaPublicKey.getModulus().toByteArray().length);
        dos.write(rsaPublicKey.getModulus().toByteArray());
        publicKeyEncoded = new String(Base64.encodeBase64(byteOs.toByteArray()));
        return "ssh-rsa " + publicKeyEncoded + " " + user;
    }

    public static String encodePrivateRsaKey(PrivateKey privateKey) {
        StringBuilder result;

        byte[] privateKeyEnc = privateKey.getEncoded();
        String privateKeyPemStr = Base64.encodeBase64String(privateKeyEnc);
        result = new StringBuilder();
        result.append("-----BEGIN RSA PRIVATE KEY-----\n");
        int column = 0;
        for(int n = 0; n < privateKeyPemStr.length(); n ++) {
            result.append(privateKeyPemStr.charAt(n));
            column ++;
            if(column == 64) {
                result.append('\n');
                column = 0;
            }
        }
        result.append("\n-----END RSA PRIVATE KEY-----");
        return result.toString();
    }
}
