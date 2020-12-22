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
package net.oneandone.stool.cli;

import java.util.ArrayList;
import java.util.List;

public class Reference {
    public final Client client;
    public final String stage;

    public Reference(Client client, String stage) {
        this.client = client;
        this.stage = stage;
    }

    public static List<Reference> list(Client client, List<String> stages) {
        List<Reference> result;

        result = new ArrayList<>(stages.size());
        for (String stage : stages) {
            result.add(new Reference(client, stage));
        }
        return result;
    }

    public int hashCode() {
        return stage.hashCode();
    }

    public boolean equals(Object object) {
        Reference reference;

        if (object instanceof Reference) {
            reference = (Reference) object;
            return stage.equals(reference.stage) && client.equals(reference.client);
        }
        return false;
    }

    public String toString() {
        return stage + "@" + client.getContext();
    }
}
