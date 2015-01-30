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

import com.oneandone.sales.tools.devreg.model.Developer;

import java.util.Date;


public class Change {
    private final long revision;
    private final Developer developer;
    private final String message;
    private final Date date;

    public Change(long revision, Developer developer, String message, long timestamp) {
        this.revision = revision;
        this.developer = developer;
        this.message = message;
        this.date = new Date(timestamp);
    }
    public long getRevision() {
        return revision;
    }
    public Developer getDeveloper() {
        return developer;
    }
    public String getMessage() {
        return message;
    }
    public Date getDate() {
        return new Date(date.getTime());
    }

}
