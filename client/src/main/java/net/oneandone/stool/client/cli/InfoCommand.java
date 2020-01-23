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
import net.oneandone.stool.client.Client;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.Reference;
import net.oneandone.stool.client.ServerManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
        for (Client client : selectedClients(globals.servers())) {
            doRun(client, globals.servers().clientFilter(all ? "" : stageClause));
        }
        return new EnumerationFailed();
    }

    private List<Client> selectedClients(ServerManager serverManager) throws IOException {
        int count;

        count = (stageClause != null ? 1 : 0) + (all ? 1 : 0);
        switch (count) {
            case 0:
                return defaultClients(serverManager);
            case 1:
                return serverManager.connectMatching(serverManager.serverFilter(all ? null : stageClause));
            default:
                throw new ArgumentException("too many select options");
        }
    }

    private List<Client> defaultClients(ServerManager serverManager) throws IOException {
        Project project;
        Reference reference;

        project = Project.lookup(world.getWorking());
        if (project != null) {
            reference = project.getAttachedOpt(serverManager);
            if (reference != null) {
                return Collections.singletonList(reference.client);
            }
        }
        return Collections.emptyList();
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
