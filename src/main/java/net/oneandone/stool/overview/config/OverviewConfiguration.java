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
package net.oneandone.stool.overview.config;

import net.oneandone.maven.embedded.Maven;
import net.oneandone.stool.overview.IndexController;
import net.oneandone.stool.overview.StageInfoCache;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.Users;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Properties;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@ComponentScan(basePackageClasses = {IndexController.class})
public class OverviewConfiguration {
    @Bean
    public World world() {
        return new World();
    }

    @Bean
    public FileNode home() {
        return world().file(System.getProperty("overview.stool.home"));
    }

    @Bean
    public String user() {
        return System.getProperty("user.name");
    }

    @Bean
    public Console console() {
        return Console.create(world());
    }

    @Bean
    public Session session() throws IOException {
        Environment system;
        FileNode home;
        String user;
        FileNode props;
        Properties p;
        String svnuser;
        String svnpassword;

        system = Environment.loadSystem();
        home = home();
        props = home.join("conf/overview.properties");
        if (props.exists()) {
            p = props.readProperties();
            svnuser = p.getProperty("svnuser");
            svnpassword = p.getProperty("svnpassword");
        } else {
            svnuser = null;
            svnpassword = null;
        }
        user = user();
        system.setStoolHome(home);
        return Session.load(Logging.forHome(home, user), user, "overview", system, console(), null, svnuser, svnpassword);
    }

    @Bean
    public Stage self() throws IOException {
        return session().load("overview");
    }

    @Bean
    public FileNode logs() throws IOException {
        return self().shared().join("tomcat/logs");
    }

    @Bean
    public Users users() throws IOException {
        return session().users;
    }

    @Bean
    public Maven maven() throws IOException {
        Maven maven;

        maven = self().maven();
        maven.getRepositorySession().setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        return maven;
    }

    @Bean
    public ExecutorService executorService() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    public StageInfoCache stages() {
        return new StageInfoCache();
    }

    @Bean
    public Timer autoRefresh() throws IOException {
        Timer timer;

        timer = new Timer("Refresh");
        timer.schedule(new RefreshTask(session(), logs()), 20000, 60000);
        return timer;
    }
}
