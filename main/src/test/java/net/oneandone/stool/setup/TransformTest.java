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
package net.oneandone.stool.setup;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TransformTest {
    @Test
    public void nop() {
        String result;

        result = Transform.transform(
                "{\n  \"version\": \"3.0.0-SNAPSHOT\"\n}\n",
                new Upgrade() {
                });
        assertEquals("{\n  \"version\": \"3.0.0-SNAPSHOT\"\n}", result);
    }

    @Test
    public void remove() {
        String result;

        result = Transform.transform(
                "{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"stages\": \"/Users/mhm\",\n  \"field\": \"foo\"\n}\n",
                new Upgrade() {
                    void fieldRemove() {
                    }
                });
        assertEquals("{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"stages\": \"/Users/mhm\"\n}", result);
    }

    @Test
    public void rename() {
        String result;

        result = Transform.transform(
                "{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"before\": \"/Users/mhm\"\n}\n",
                new Upgrade() {
                    String beforeRename() {
                        return "after";
                    }
                });
        assertEquals("{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"after\": \"/Users/mhm\"\n}", result);
    }

    @Test
    public void transform() {
        String result;

        result = Transform.transform(
                "{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"stages\": 5\n}\n",
                new Upgrade() {
                    JsonElement stagesTransform(JsonElement orig) { return new JsonPrimitive(orig.getAsInt() * 2); }
                });
        assertEquals("{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"stages\": 10\n}", result);
    }

    @Test
    public void global() {
        String result;

        result = Transform.transform(
                "{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"stages\": 5\n}\n",
                new Upgrade() {
                    void global(JsonObject src, JsonObject dest) {
                        dest.add("foo", new JsonPrimitive("bar"));
                    }
                });
        assertEquals("{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"stages\": 5,\n  \"foo\": \"bar\"\n}", result);
    }
}
