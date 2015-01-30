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
package net.oneandone.stool.stage.artifact;

import net.oneandone.stool.users.User;

import java.util.Date;


public class Change {
    private final long revision;
    private final User user;
    private final String message;
    private final Date date;

    public Change(long revision, User user, String message, long timestamp) {
        this.revision = revision;
        this.user = user;
        this.message = message;
        this.date = new Date(timestamp);
    }
    public long getRevision() {
        return revision;
    }
    public User getUser() {
        return user;
    }
    public String getMessage() {
        return message;
    }
    public Date getDate() {
        return new Date(date.getTime());
    }

}
