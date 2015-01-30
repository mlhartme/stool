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
package net.oneandone.stool.devreg;

public class User {
    public final String login;
    public final String name;
    public final String email;
    public final String department;
    public final String phone;

    public User(String login, String name, String email, String department, String phone) {
        this.login = login;
        this.name = name;
        this.email = email;
        this.department = department;
        this.phone = phone;
    }

    //-- Object methods

    public String toString() {
        return "login: " + login + "\n"
                + "name: " + name + "\n"
                + "email: " + email + "\n"
                + "department: " + department + "\n"
                + "phone: " + phone;
    }

    @Override
    public boolean equals(Object object) {
        User developer;

        if (!(object instanceof User)) {
            return false;
        }
        developer = (User) object;
        return login.equals(developer.login) && name.equals(developer.name) && email.equals(developer.email)
                && department.equals(developer.department) && phone.equals(developer.phone);
    }

    public int hashCode() {
        return login.hashCode();
    }
}
