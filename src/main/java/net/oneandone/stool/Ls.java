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
package net.oneandone.stool;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Lock;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.List;

public class Ls extends StageCommand {
    private static final int STATE_WIDTH = 10;
    private static final int OWNER_WIDTH = 15;

    private static final String NOT_SELECTED = "   ";

    public Ls(Session session) {
        super(session);
    }

    @Override
    protected Lock lock() {
        return null;
    }

    @Override
    public boolean doBefore(List<Stage> stages, int indent) throws IOException {
        header("stages");
        message(Strings.padLeft("{name}   ", indent) + NOT_SELECTED + Strings.padRight("state", STATE_WIDTH)
          + Strings.padRight("owner", OWNER_WIDTH) + "url");
        message("");
        return true;
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        String colState;
        String colOwner;

        colState = Strings.padRight(stage.state().toString(), STATE_WIDTH);
        colOwner = Strings.padRight(stage.owner(), OWNER_WIDTH);
        message((session.isSelected(stage) ? " * " : NOT_SELECTED) + colState + colOwner + stage.getUrl());
    }

    @Override
    public void doAfter() throws IOException {
        int padStorage = 8;
        message("");
        header("storage");
        message("   mem free: " + Strings.padLeft("~" + session.memUnreserved() + " Mb", padStorage));
        message("   disk free:" + Strings.padLeft("~" + session.diskFree() + " Mb", padStorage));
        message("");
    }

    protected List<Stage> defaultSelected(EnumerationFailed problems) throws IOException {
        return all(problems);
    }
}
