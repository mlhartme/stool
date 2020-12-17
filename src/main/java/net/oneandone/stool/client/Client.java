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
package net.oneandone.stool.client;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class Client {
    public Client() {
    }

    public abstract String getContext();

    public abstract String version() throws IOException;

    public List<String> list(String filter) throws IOException {
        return new ArrayList<>(list(filter, Collections.emptyList()).keySet());
    }
    public abstract Map<String, Map<String, JsonElement>> list(String filter, List<String> select) throws IOException;

    /**
     * @return image actually started
     * @throws FileAlreadyExistsException if the stage already exists */
    public abstract Map<String, String> create(String stage, String image, Map<String, String> values) throws IOException;

    /** @return image actually started */
    public abstract String publish(String stage, String imageOpt, Map<String, String> values) throws IOException;
    public abstract Map<String, String> awaitAvailable(String stage) throws IOException;
    public abstract void delete(String stage) throws IOException;
    public abstract PodConfig podToken(String stage, int timeout) throws IOException;
    public abstract List<String> history(String stage, boolean details, int max) throws IOException;
    public abstract List<String> validate(String stage, boolean email, boolean repair) throws IOException;
    public abstract Map<String, String> getValues(String stage) throws IOException;
    public abstract Map<String, String> setValues(String stage, Map<String, String> values) throws IOException;
    public abstract List<String> images(String stage) throws Exception;
}
