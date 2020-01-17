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

import net.oneandone.stool.client.Client;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Reference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class InfoCommand extends StageCommand {

    protected final List<String> selected = new ArrayList<>();

    public InfoCommand(Globals globals) {
        super(globals);
    }

    /* Note that the stage is not locked when this method is called. @return true to use prefix stream. */
    public boolean doBefore(List<Reference> names, int indent) throws IOException {
        return names.size() != 1;
    }

    //--

    /* Note that the stage is not locked when this method is called. */
    public void doAfter() throws IOException {
    }

    @Override
    public EnumerationFailed runAll(List<Reference> lst, int width) throws Exception {
        Client client;

        doBefore(lst, width);
        lst = new ArrayList<>(lst);
        while (!lst.isEmpty()) {
            client = lst.get(0).client;
            doRun(client, eat(lst, client));
        }
        doAfter();
        return new EnumerationFailed();
    }

    public abstract void doRun(Client client, List<String> stages) throws Exception;

    private List<String> eat(List<Reference> lst, Client client) {
        List<String> result;
        Iterator<Reference> iter;
        Reference ref;

        result = new ArrayList<>();
        iter = lst.iterator();
        while (iter.hasNext()) {
            ref = iter.next();
            if (client == ref.client) {
                result.add(ref.stage);
                iter.remove();
            }
        }
        return result;
    }

    public void select(String str) {
        selected.add(str);
    }
}
