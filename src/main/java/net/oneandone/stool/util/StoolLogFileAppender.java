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
package net.oneandone.stool.util;

import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.recovery.ResilientFileOutputStream;
import ch.qos.logback.core.util.FileUtil;
import org.apache.commons.io.output.NullOutputStream;

import java.io.File;
import java.io.IOException;

/**
 * Basicly it's the smae as the standard Fileappender, except of not creating files in not existing directories
 */
public class StoolLogFileAppender<E> extends FileAppender<E> {
    @Override
    public void openFile(String filename) throws IOException {
        synchronized (lock) {
            File file = new File(filename);
            if (FileUtil.isParentDirectoryCreationRequired(file)) {
                    addError("Parent does not exist. Ignoring. ["
                            + file.getAbsolutePath() + "]");
                setOutputStream(new NullOutputStream());
            } else {
                ResilientFileOutputStream resilientFos = new ResilientFileOutputStream(
                    file, append);
                resilientFos.setContext(context);
                setOutputStream(resilientFos);

            }
        }
    }
}
