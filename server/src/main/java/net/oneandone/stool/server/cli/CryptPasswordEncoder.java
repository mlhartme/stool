package net.oneandone.stool.server.cli;

import net.oneandone.sushi.util.Strings;
import org.apache.commons.codec.digest.Md5Crypt;
import org.apache.commons.codec.digest.UnixCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;

public class CryptPasswordEncoder implements PasswordEncoder {
    public static void main(String[] args) {
        System.out.println(Md5Crypt.md5Crypt("passwd".getBytes(), "salt"));
        System.out.println(new CryptPasswordEncoder().matches("passwd", "fromLdap"));
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return UnixCrypt.crypt(rawPassword.toString());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        final String prefix = "{CRYPT}";
        int idx;
        String salt;
        String match;
        boolean result;

        encodedPassword = Strings.removeLeft(encodedPassword, prefix);
        idx = encodedPassword.lastIndexOf('$');
        if (idx < 1) {
            // CAUTION: an initial $ at index 0 is a problem
            throw new IllegalArgumentException(encodedPassword);
        }
        salt = encodedPassword.substring(0, idx + 1);
        match = Md5Crypt.md5Crypt(rawPassword.toString().getBytes(), salt);
        result = match.equals(encodedPassword);
        return result;
    }
}
