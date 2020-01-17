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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.Reference;
import net.oneandone.stool.client.ServerManager;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class StageCommand extends ClientCommand {
    protected String stageClause;
    protected boolean all;
    protected Fail fail = Fail.NORMAL;

    public StageCommand(Globals globals) {
        super(globals);
    }

    public void setStage(String clause) {
        this.stageClause = clause;
    }

    public void setAll(boolean all) {
        this.all = all;
    }

    public void setFail(Fail fail) {
        this.fail = fail;
    }

    //--

    @Override
    public void run() throws Exception {
        int width;
        List<Reference> lst;
        EnumerationFailed failures;
        String failureMessage;

        lst = selectedList(globals.servers());
        width = 0;
        for (Reference reference : lst) {
            width = Math.max(width, reference.toString().length());
        }
        width += 5;
        failures = runAll(lst, width);
        failureMessage = failures.getMessage();
        if (failureMessage != null) {
            switch (fail) {
                case AFTER:
                    throw failures;
                case NEVER:
                    console.info.println("WARNING: " + failureMessage);
                    break;
                default:
                    throw new IllegalStateException("unknown fail mode: " + fail.toString());
            }
        }
    }

    public abstract EnumerationFailed runAll(List<Reference> lst, int width) throws Exception;

    private List<Reference> selectedList(ServerManager serverManager) throws IOException {
        int count;

        count = (stageClause != null ? 1 : 0) + (all ? 1 : 0);
        switch (count) {
            case 0:
                return defaultSelected(serverManager);
            case 1:
                return serverManager.list(all ? null : stageClause);
            default:
                throw new ArgumentException("too many select options");
        }
    }

    /** override this to change the default */
    protected List<Reference> defaultSelected(ServerManager serverManager) throws IOException {
        Project project;
        Reference reference;

        project = Project.lookup(world.getWorking());
        if (project != null) {
            reference = project.getAttachedOpt(serverManager);
            if (reference != null) {
                return Collections.singletonList(reference);
            }
        }
        return Collections.emptyList();
    }

    public enum Fail {
        NORMAL, AFTER, NEVER
    }


    //--

    protected static Map<String, String> selection(List<String> selection) {
        int idx;
        Map<String, String> result;

        result = new LinkedHashMap<>();
        for (String image : selection) {
            idx = image.indexOf(':');
            if (idx == -1) {
                result.put(image, "");
            } else {
                result.put(image.substring(0, idx), image.substring(idx + 1));
            }
        }
        return result;
    }
}
