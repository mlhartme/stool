package net.oneandone.stool.server.web;

import net.oneandone.stool.server.Server;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jmx.support.ConnectorServerFactoryBean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ServerConfigurer implements WebMvcConfigurer {
    @Bean
    public World world() throws IOException {
        return World.create();
    }

    @Bean
    public Server server(World world) throws IOException {
        return Server.create(world);
    }

    @Bean  // TODO: still needed?
    public ExecutorService executorService() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    public FileNode logs(World world) {
        return world.getWorking().join("TODO/logs");
    }

    @Bean
    public ConnectorServerFactoryBean serverConnector() {
        // as suggested by
        //   https://stackoverflow.com/questions/31257968/how-to-access-jmx-interface-in-docker-from-outside
        Server.LOGGER.info("serverConnector created");
        return new ConnectorServerFactoryBean();
    }

    @Autowired
    private ApiLogging loggingInitializer;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInitializer).addPathPatterns("/api/**");
    }
}