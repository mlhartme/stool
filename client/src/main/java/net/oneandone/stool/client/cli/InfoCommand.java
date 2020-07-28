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
package net.oneandone.stool.client.cli;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.client.App;
import net.oneandone.stool.client.Client;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.Reference;
import net.oneandone.stool.client.Configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class InfoCommand extends StageCommand {
    protected final List<String> selected = new ArrayList<>();

    public InfoCommand(Globals globals) {
        super(globals);
    }

    public void select(String str) {
        selected.add(str);
    }

    @Override
    public EnumerationFailed runAll() throws Exception {
        Map<Client, String> clientFilters;
        Configuration configuration;
        List<Reference> references;
        String clientFilter;

        if (stageClause != null && all) {
            throw new ArgumentException("too many select options");
        }
        configuration = globals.configuration();
        clientFilters = new LinkedHashMap<>();
        if (all) {
            clientFilters.put(configuration.contextConnect(), "");
        } else if (stageClause != null) {
            clientFilters.put(configuration.contextConnect(), stageClause);
        } else {
            references = projectReferences(configuration);
            if (references.isEmpty()) {
                clientFilters.put(configuration.contextConnect(), "");
            } else {
                for (Reference reference : references) {
                    clientFilter = clientFilters.get(reference.client);
                    if (clientFilter != null) {
                        clientFilter = clientFilter + "," + reference.stage;
                    } else {
                        clientFilter = reference.stage;
                    }
                    clientFilters.put(reference.client, clientFilter);
                }
            }
        }
        for (Map.Entry<Client, String> entry : clientFilters.entrySet()) {
            doRun(entry.getKey(), entry.getValue());
        }
        return new EnumerationFailed();
    }

    private List<Reference> projectReferences(Configuration configuration) throws IOException {
        Project project;
        List<Reference> result;

        project = Project.lookup(working, configuration);
        result = new ArrayList<>();
        if (project != null) {
            for (App app : project.list()) {
                result.add(app.reference);
            }
        }
        return result;
    }

    public static String infoToString(JsonElement info) {
        StringBuilder result;
        JsonPrimitive p;

        if (info.isJsonPrimitive()) {
            p = info.getAsJsonPrimitive();
            if (p.isString()) {
                return p.getAsString();
            } else {
                return p.toString();
            }
        } else if (info.isJsonArray()) {
            result = new StringBuilder();
            for (JsonElement e : info.getAsJsonArray()) {
                if (result.length() > 0) {
                    result.append(", ");
                }
                result.append(infoToString(e));
            }
            return result.toString();
        } else if (info.isJsonObject()) {
            result = new StringBuilder();
            for (Map.Entry<String, JsonElement> m : info.getAsJsonObject().entrySet()) {
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

    public abstract void doRun(Client client, String clientFilter) throws Exception;
}
