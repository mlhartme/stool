package net.oneandone.stool.server.cli;

import net.oneandone.stool.server.util.ApplicationContext;
import net.oneandone.sushi.fs.World;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class ServerConfig implements WebMvcConfigurer {
    @Bean
    public ApplicationContext context() throws IOException {
        World world;
        Globals globals;

        world = World.create();
        globals = Globals.create(world);
        return globals.context();
    }

    @Autowired
    private ApiLogging loggingInitializer;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        System.out.println("add " + registry + " " + loggingInitializer);
        registry.addInterceptor(loggingInitializer).addPathPatterns("/api/**");
    }
}