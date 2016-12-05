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
package net.oneandone.stool.stage.artifact;

import net.oneandone.stool.users.Users;
import net.oneandone.sushi.fs.file.FileNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Applications {
    private final List<Application> apps;
    private Changes lazyChanges;


    public Applications() {
        apps = new ArrayList<>();
    }

    public void add(Application application) {
        apps.add(application);
    }

    public List<Application> applications() {
        return Collections.unmodifiableList(apps);
    }

    public Changes changes(FileNode backstage, Users users) {
        if (lazyChanges == null) {
            lazyChanges = new Changes();
            /* TODO disabled for now ...
            for (Application app : apps) {
                lazyChanges.merge(app.changes(shared, users));
            }*/
        }
        return lazyChanges;
    }

    public int size() {
        return apps.size();
    }
}
