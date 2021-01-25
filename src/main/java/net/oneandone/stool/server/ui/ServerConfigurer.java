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
package net.oneandone.stool.server.ui;

import net.oneandone.stool.Main;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.sushi.fs.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class ServerConfigurer implements WebMvcConfigurer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConfigurer.class);

    @Bean
    public World world() throws IOException {
        return World.create();
    }

    @Bean
    public net.oneandone.stool.core.Configuration configuration(World world) throws IOException {
        net.oneandone.stool.core.Configuration result;

        LOGGER.info("server version " + Main.versionString(world));
        result = net.oneandone.stool.core.Configuration.load(world);
        LOGGER.info("server configuration:");
        LOGGER.info(result.toYaml().toPrettyString());
        return result;
    }

    @Bean
    public UserManager userManager(net.oneandone.stool.core.Configuration configuration) throws IOException {
        return configuration.createUserManager();
    }
}
