package net.oneandone.stool.server.web;

import net.oneandone.stool.server.Server;
import net.oneandone.sushi.fs.World;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jmx.support.ConnectorServerFactoryBean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class ServerConfigurer implements WebMvcConfigurer {
    @Bean
    public Server server() throws IOException {
        return Server.create(World.create());
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
        System.out.println("add " + registry + " " + loggingInitializer);
        registry.addInterceptor(loggingInitializer).addPathPatterns("/api/**");
    }
}