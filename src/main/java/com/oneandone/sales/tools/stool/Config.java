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
package com.oneandone.sales.tools.stool;

import com.oneandone.sales.tools.stool.configuration.Option;
import com.oneandone.sales.tools.stool.configuration.StageConfiguration;
import com.oneandone.sales.tools.stool.stage.Stage;
import com.oneandone.sales.tools.stool.util.Role;
import com.oneandone.sales.tools.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Remaining;

import javax.naming.NoPermissionException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class Config extends StageCommand {
    private Map<String, String> properties;

    private boolean get;
    private boolean set;

    public Config(Session session) throws IOException {
        super(session);
        properties = new HashMap<>();
    }

    @Remaining
    public void property(String str) throws NoPermissionException {
        int idx;
        String key;
        String value;
        Role role;

        role = null;

        if (session.configuration.security.isWaterloo()) {
            role = Role.detect(session.configuration);
        }

        idx = str.indexOf('=');
        if (idx == -1) {
            key = str;
            value = null;
            get = true;
        } else {
            key = str.substring(0, idx);
            value = str.substring(idx + 1);
            set = true;
        }
        try {
            if (!StageConfiguration.isConfigurable(key, role)) {
                throw new ArgumentException("unknown property: " + key);
            }

        } catch (SecurityException e) {
            throw new ArgumentException(e.getMessage());
        }
        if (properties.containsKey(key)) {
            throw new ArgumentException("duplicate property: " + key);
        }
        if (get && set) {
            throw new ArgumentException("do not mix set and get arguments");
        }
        properties.put(key, value);
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        StageConfiguration stageConfiguration;
        boolean error;

        stageConfiguration = stage.config();
        if (set) {
            stage.checkOwnership();
            error = false;
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                try {
                    stageConfiguration.configure(entry.getKey(), entry.getValue());
                    console.info.println(entry.getKey() + "=" + entry.getValue());
                } catch (Exception e) {
                    console.info.println("invalid value for property " + entry.getKey() + " : " + e.getMessage());
                    e.printStackTrace(console.verbose);
                    error = true;
                }
            }
            if (!error) {
                session.saveStageProperties(stageConfiguration, stage.wrapper);
                if (session.isSelected(stage)) {
                    session.environment.setAll(session.environment(stage));
                }
            }
        } else if (get) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                Field field;
                field = stage.config().getFieldByAnnotation(entry.getKey());

                console.info.println(entry.getKey() + "=" + field.get(stage.config()));
            }
        } else {
            printConfigurationOptions(stage);
        }

    }
    public void printConfigurationOptions(Stage stage) throws IllegalAccessException {
        for (Field f : StageConfiguration.class.getFields()) {
            if (f.isAnnotationPresent(Option.class)) {
                console.info.println('#' + f.getAnnotation(Option.class).description());
                console.info.println(f.getAnnotation(Option.class).key() + "=" + f.get(stage.config()));
                console.info.print("\n");
            }
        }
    }

}
