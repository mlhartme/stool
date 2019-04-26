package net.oneandone.stool.server.web;

import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.Globals;
import net.oneandone.sushi.fs.World;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class ServerConfigurer implements WebMvcConfigurer {
    @Bean
    public Server server() throws IOException {
        World world;
        Globals globals;

        world = World.create();
        globals = Globals.create(world);
        return globals.server();
    }

    @Autowired
    private ApiLogging loggingInitializer;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        System.out.println("add " + registry + " " + loggingInitializer);
        registry.addInterceptor(loggingInitializer).addPathPatterns("/api/**");
    }
}