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
package net.oneandone.stool.dashboard.config;

import net.oneandone.stool.dashboard.IndexController;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.users.Users;
import net.oneandone.stool.server.util.Environment;
import net.oneandone.stool.server.util.Logging;
import net.oneandone.stool.server.util.Session;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@ComponentScan(basePackageClasses = {IndexController.class})
public class DashboardConfiguration {
    @Bean
    public World world() throws IOException {
        return World.create();
    }

    @Bean
    public FileNode jar() throws IOException {
        return world().file(System.getProperty("stool.cp"));
    }

    @Bean
    public FileNode home() throws IOException {
        return world().file(System.getProperty("stool.home"));
    }

    @Bean
    public DashboardProperties properties() throws IOException {
        return DashboardProperties.load(home());
    }

    @Bean
    public Session session() throws IOException {
        DashboardProperties p;
        Logging logging;

        p = properties();
        logging = Logging.create(logs(), "dashboard", Environment.loadSystem().detectUser());
        logging.log("dashboard", "startup");
        return Session.load(home(), logging);
    }

    @Bean
    public Stage self() throws IOException {
        return null; // TODO session().load(world().file(System.getProperty("stool.idlink")));
    }

    @Bean
    public FileNode logs() throws IOException {
        return world().file(System.getProperty("catalina.base")).join("logs");
    }

    @Bean
    public Users users() throws IOException {
        return session().users;
    }

    @Bean
    public ExecutorService executorService() {
        return Executors.newCachedThreadPool();
    }
}
