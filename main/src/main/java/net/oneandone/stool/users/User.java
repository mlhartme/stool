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

public class User {
    public final String login;
    public final String name;
    public final String email;

    public User(String login, String name, String email) {
        this.login = login;
        this.name = name;
        this.email = email;
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

    public boolean isGenerated() {
        return login.equals(name);
    }

    public int hashCode() {
        return login.hashCode();
    }
}
