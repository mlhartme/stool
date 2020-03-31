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
package net.oneandone.stool.server.kubernetes;

public class DataType {
    public static DataType configMap(String name, String path) {
        return new DataType(false, name, path);
    }
    public static DataType secrets(String name, String path) {
        return new DataType(true, name, path);
    }

    public final boolean secret;
    public final String name;
    public final String path;

    private DataType(boolean secret, String name, String path) {
        this.secret = secret;
        this.name = name;
        this.path = path;
    }
}
