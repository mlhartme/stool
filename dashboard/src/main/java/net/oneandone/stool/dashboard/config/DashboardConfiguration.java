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

import net.oneandone.inline.Console;
import net.oneandone.maven.embedded.Maven;
import net.oneandone.stool.dashboard.IndexController;
import net.oneandone.stool.stage.Project;
import net.oneandone.stool.users.Users;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Timer;
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
    public Console console() {
        return Console.create();
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
        return Session.load(false, home(), logging, "dashboard", console(), world(), p.svnuser, p.svnpassword);
    }

    @Bean
    public Project self() throws IOException {
        return Project.load(session(), world().file(System.getProperty("stool.idlink")));
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
    public Maven maven() throws IOException {
        return self().maven();
    }

    @Bean
    public ExecutorService executorService() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    public Timer autoRefresh() throws IOException {
        Timer timer;

        timer = new Timer("Refresh");
        timer.schedule(new RefreshTask(jar(), session(), logs()), 20000, 60000);
        return timer;
    }
}
