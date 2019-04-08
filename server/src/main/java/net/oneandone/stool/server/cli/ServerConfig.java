package net.oneandone.stool.server.cli;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ServerConfig implements WebMvcConfigurer {
    @Autowired
    private ApiLogging loggingInitializer;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        System.out.println("add " + registry + " " + loggingInitializer);
        registry.addInterceptor(loggingInitializer).addPathPatterns("/api/**");
    }
}