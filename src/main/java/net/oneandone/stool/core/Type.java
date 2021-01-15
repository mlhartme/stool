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
package net.oneandone.stool.core;

/**
 * Things that Stool assumes about helm charts.
 */
public final class Type {
    public static final String VALUE_REPLICAS = "replicas";

    public static final String VALUE_CONTACT = "metadataContact";
    public static final String VALUE_EXPIRE = "metadataExpire";
    public static final String VALUE_COMMENT = "metadataComment";

    public static final String[] MANDATORY = {
            VALUE_REPLICAS, VALUE_CONTACT, VALUE_COMMENT, VALUE_EXPIRE
    };

    public static final String MAIN_CONTAINER = "main"; // TODO: needed for monitoring stats ...

    /** @param name = stage name = help release name */
    public static String deploymentName(String name) {
        // is not allowed to contain dots
        return name.replace(".", "--");
    }

    private Type() {
    }

}
