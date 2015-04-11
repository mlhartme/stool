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
package net.oneandone.stool.configuration;

/**
 * A Security level describes if a user can run a specified command
 */
public enum SecurityLevel {
    LOCAL, GEM, PEARL;

    public boolean isGem() {
        return this == GEM;
    }

    public boolean isPearl() {
        return this == PEARL;
    }

    public boolean isLocal() {
        return this == LOCAL;
    }

    public boolean isShared() {
        return this != LOCAL;
    }
}
