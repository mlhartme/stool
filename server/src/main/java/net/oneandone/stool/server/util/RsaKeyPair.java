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
package net.oneandone.stool.server.util;

import net.oneandone.sushi.fs.World;
import org.apache.tomcat.util.codec.binary.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

/**
 * Based on https://stackoverflow.com/questions/3706177/how-to-generate-ssh-compatible-id-rsa-pub-from-java
 */
public class RsaKeyPair {
    public static void main(String[] args) throws IOException {
        RsaKeyPair pair;
        World world;

        System.out.println("generating ...");
        pair = generate();
        world = World.create();
        world.file("id_rsa.pub").writeString(pair.publicKey("stool-tunnel"));
        world.file("id_rsa").writeString(pair.privateKey());
        System.out.println("done");
    }

    public static RsaKeyPair generate() {
        KeyPairGenerator generator;

        try {
            generator = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        generator.initialize(2048);
        return new RsaKeyPair(generator.genKeyPair());
    }

    private final KeyPair pair;

    private RsaKeyPair(KeyPair pair) {
        this.pair = pair;
    }

    public String publicKey(String user) {
        RSAPublicKey rsaPublicKey;
        ByteArrayOutputStream dest;

        rsaPublicKey = (RSAPublicKey) pair.getPublic();
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

    public String privateKey() {
        StringBuilder result;
        byte[] bytes;
        int width;

        bytes = Base64.encodeBase64(pair.getPrivate().getEncoded());
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
