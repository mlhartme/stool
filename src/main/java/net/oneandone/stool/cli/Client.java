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

import com.fasterxml.jackson.databind.JsonNode;
import net.oneandone.stool.helmclasses.ClassRef;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** sort-lived, created per request/cli invocation */
public abstract class Client {
    private final String context;
    protected final Caller caller;

    public Client(String context, Caller caller) {
        this.context = context;
        this.caller = caller;
    }

    public String getContext() {
        return context;
    }

    public boolean equals(Object object) {
        if (object instanceof Client) {
            return context.equals(((Client) object).getContext());
        }
        return false;
    }

    public int hashCode() {
        return context.hashCode();
    }

    public abstract String version() throws IOException;

    public List<String> list(String filter) throws IOException {
        return new ArrayList<>(list(filter, Collections.emptyList(), false).keySet());
    }
    /** @param select  empty list to select all fields */
    public abstract Map<String, Map<String, JsonNode>> list(String filter, List<String> select, boolean hidden) throws IOException;

    /**
     * @return image actually started
     * @throws FileAlreadyExistsException if the stage already exists */
    public abstract Map<String, String> create(String stageName, ClassRef classRef, Map<String, String> values) throws IOException;

    public abstract void publish(String stage, ClassRef classRef, Map<String, String> values) throws IOException;
    public abstract Map<String, String> awaitAvailable(String stage) throws IOException;
    public abstract void delete(String stage) throws IOException;
    public abstract Map<String, String> getValues(String stage) throws IOException;
    public abstract Map<String, String> setValues(String stage, Map<String, String> values) throws IOException;
    public abstract List<String> history(String stage) throws IOException;
    public abstract List<String> validate(String stage, boolean email, boolean repair) throws IOException;
    public abstract PodConfig podToken(String stage, int timeout) throws IOException;

    public abstract List<String> images(String image) throws Exception;
}
