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
package net.oneandone.stool.server;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Things that Stool assumes about helm charts.
 *
 * TODO: stage name -&gt; deployment name is not represented in this class
 */
public class Type {
    public static final String VALUE_REPLICAS = "replicas";
    public static final String VALUE_IMAGE = "image";

    public static final String VALUE_CONTACT = "metadataContact";
    public static final String VALUE_EXPIRE = "metadataExpire";
    public static final String VALUE_COMMENT = "metadataComment";

    public static final String[] MANDATORY_VALUES = {
            VALUE_IMAGE, VALUE_REPLICAS,
            VALUE_COMMENT, VALUE_EXPIRE, VALUE_CONTACT
    };

    public static final String MAIN_CONTAINER = "main"; // TODO: needed for monitoring stats ...

    /** @param name = stage name = help release name */
    public static String deploymentName(String name) {
        // is not allowed to contain dots
        return name.replace(".", "--");
    }

    //--

    public static final Type TYPE = new Type();

    public Type() {
    }

    public void checkValues(Map<String, String> clientValues, Collection<String> builtIns) {
        Set<String> unknown;

        unknown = new HashSet<>(clientValues.keySet());
        unknown.removeAll(builtIns);
        if (!unknown.isEmpty()) {
            throw new ArgumentException("unknown value(s): " + unknown);
        }
    }
}
