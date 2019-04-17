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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/** A stool user. Not that a user does not necessarily correspond to an OS user (i.e. a user account on the current machine) */
public class User implements UserDetails {
    public static final User ANONYMOUS = new User("anonymous", "Anonymous", null);

    public static User fromJson(JsonObject obj) {
        JsonElement email;

        email = obj.get("email");
        return new User(str(obj, "login"), str(obj, "name"), email == null ? null : email.getAsString());
    }

    private static String str(JsonObject obj, String name) {
        return obj.get(name).getAsString();
    }

    public final String login;
    public final String name;

    /** may be null */
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

    public JsonObject toJson() {
        JsonObject result;

        result = new JsonObject();
        result.add("login", new JsonPrimitive(login));
        result.add("name", new JsonPrimitive(name));
        if (email != null) {
            result.add("email", new JsonPrimitive(email));
        }
        return result;
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
            return login.equals(user.login) && name.equals(user.name) && Objects.equals(email, email);
        }
        return false;
    }

    public String toStatus() {
        if (email == null) {
            return name;
        } else {
            return name + " <" + email + ">";
        }
    }

    public int hashCode() {
        return login.hashCode();
    }


    //-- UserDetails implementation

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
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
