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
package com.oneandone.sales.tools.stool.util;

import java.util.Map;

public class Macros {

    private final Map<String, String> macros;


    public Macros(Map<String, String> marcos) {
        this.macros = marcos;
    }


    public String replace(String string) {
        return replace(string, '@', '@');
    }

    public String replace(String string, Character prefix, Character suffix) {
        String result;
        String macroKey;

        result = string;
        for (Map.Entry<String, String> macro : macros.entrySet()) {
            macroKey = String.format("%s%s%s", prefix, macro.getKey(), suffix);
            result = result.replace(macroKey, macro.getValue());
        }

        return result;
    }

}
