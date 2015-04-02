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

import net.oneandone.stool.configuration.SecurityLevel;
import net.oneandone.stool.configuration.StoolConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RoleTest {

    @Test
    public void isAdmin() throws Exception {
        assertTrue(Role.isAdmin(new StoolConfiguration(null)));
    }

    @Test
    public void isNotAnAdmin() throws Exception {
        StoolConfiguration stoolConfiguration;

        stoolConfiguration = new StoolConfiguration(null);
        stoolConfiguration.adminGroup = "HopefullyNotTrueElseIThinkYourGroupSystemIsCrazy";
        stoolConfiguration.security = SecurityLevel.GEM;
        assertFalse(Role.isAdmin(stoolConfiguration));
    }
}
