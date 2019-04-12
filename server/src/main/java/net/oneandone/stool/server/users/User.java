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
package net.oneandone.stool.server.users;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/** A stool user. Not that a user does not necessarily correspond to an OS user (i.e. a user account on the current machine) */
public class User implements UserDetails {
    public final String login;
    public final String name;
    public final String email;

    private final String password; // because it's used for authentication

    public User(String login, String name, String email) {
        this(login, name, email, null);
    }

    public User(String login, String name, String email, String password) {
        this.login = login;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    //-- Object methods

    public String toString() {
        return "login: " + login + "\n"
                + "name: " + name + "\n"
                + "email: " + email + "\n";
    }

    @Override
    public boolean equals(Object object) {
        User user;

        if (object instanceof User) {
            user = (User) object;
            return login.equals(user.login) && name.equals(user.name) && email.equals(user.email);
        }
        return false;
    }

    public String toStatus() {
        if (isGenerated()) {
            return login;
        } else {
            return name + " <" + email + ">";
        }
    }

    public boolean isGenerated() {
        return login.equals(name);
    }

    public int hashCode() {
        return login.hashCode();
    }


    //-- UserDetails implementation

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new GrantedAuthority() { // TODO
            @Override
            public String getAuthority() {
                return "ROLE_LOGIN";
            }
        });
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
