/**
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
package net.oneandone.stool.users;

import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.sushi.fs.World;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Users {
    public static Users fromLdap(String url, String principal, String credentials) {
        return new Users(Ldap.create(url, principal, credentials));
    }

    public static Users fromLogin() {
        return new Users(null);
    }

    //--

    private final Ldap ldap;
    private final Map<String, User> users;

    private Users(Ldap ldap) {
        this.ldap = ldap;
        this.users = new HashMap<>();
    }

    /** @return never null */
    public User byLogin(String login) throws UserNotFound, NamingException {
        User user;

        user = users.get(login);
        if (user == null) {
            if (ldap == null) {
                user = new User(login, login, login + "@localhost");
            } else {
                user = ldap.lookup(login);
            }
            users.put(login, user);
        }
        return user;
    }
}
