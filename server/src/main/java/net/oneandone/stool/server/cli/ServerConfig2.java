package net.oneandone.stool.server.cli;

import net.oneandone.stool.server.util.Session;
import net.oneandone.sushi.fs.World;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class ServerConfig2 implements WebMvcConfigurer {
    @Bean
    public Session session() throws IOException {
        World world;
        Globals globals;

        world = World.create();
        globals = Globals.create(world);
        return globals.session();
    }
}