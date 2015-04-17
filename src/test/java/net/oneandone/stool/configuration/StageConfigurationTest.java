package net.oneandone.stool.configuration;

import com.google.gson.Gson;
import net.oneandone.stool.extensions.ExtensionsFactory;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.junit.Test;

import java.io.IOException;

public class StageConfigurationTest {
    @Test
    public void io() throws IOException {
        World world;
        ExtensionsFactory factory;
        Gson gson;
        StageConfiguration configuration;
        FileNode tmp;

        world = new World();
        factory = ExtensionsFactory.create(world);
        gson = Session.gson(world, factory);
        configuration = new StageConfiguration("a", "b", "c", factory.newInstance());
        tmp = world.getTemp().createTempDirectory();
        configuration.save(gson, tmp);
        StageConfiguration.load(gson, tmp);
    }
}
