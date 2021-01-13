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
package net.oneandone.stool.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.cli.Client;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.core.Configuration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class InfoCommand extends StageCommand {
    protected final List<String> selected = new ArrayList<>();

    public InfoCommand(Globals globals, String stage) {
        super(globals, stage);
    }

    public void select(String str) {
        selected.add(str);
    }

    @Override
    public CompoundResult runAll() throws Exception {
        Map<Client, String> clientFilters;
        Configuration configuration;
        CompoundResult result;

        if (stageClause != null && all) {
            throw new ArgumentException("too many select options");
        }
        configuration = globals.configuration();
        clientFilters = new LinkedHashMap<>();
        if (all) {
            clientFilters.put(configuration.currentContextConnect(globals.caller()), "");
        } else {
            clientFilters.put(configuration.currentContextConnect(globals.caller()), stageClause);
        }
        result = new CompoundResult();
        for (Map.Entry<Client, String> entry : clientFilters.entrySet()) {
            doRun(entry.getKey(), entry.getValue(), result);
        }
        return result;
    }

    public static String infoToString(JsonNode info) {
        StringBuilder result;
        Iterator<JsonNode> array;
        Iterator<Map.Entry<String, JsonNode>> obj;
        Map.Entry<String, JsonNode> m;

        if (info.isTextual()) {
            return info.asText();
        } else if (info.isNumber()) {
            return info.toString();
        } else if (info.isArray()) {
            array = info.elements();
            result = new StringBuilder();
            while (array.hasNext()) {
                if (result.length() > 0) {
                    result.append(", ");
                }
                result.append(infoToString(array.next()));
            }
            return result.toString();
        } else if (info.isObject()) {
            obj = info.fields();
            result = new StringBuilder();
            while (obj.hasNext()) {
                m = obj.next();
                if (result.length() > 0) {
                    result.append(", ");
                }
                result.append(m.getKey()).append(" ").append(infoToString(m.getValue()));
            }
            return result.toString();
        } else {
            throw new IllegalStateException(info.toString());
        }
    }

    public abstract void doRun(Client client, String clientFilter, CompoundResult result) throws Exception;
}
