package net.oneandone.stool.server.cli;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.stool.server.util.Server;
import net.oneandone.sushi.fs.World;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;

@Controller
public class RestController {
    @GetMapping("/version") @ResponseBody
    public String version() throws IOException {
        String version;

        version = Server.versionString(World.create());
        return "<html><title>version=" + version + "</title></html>";
    }

    @GetMapping("/stage/${name}/properties") @ResponseBody
    public String properties() throws IOException {
        JsonObject result;

        System.out.println("properties");
        result = new JsonObject();
        result.add("quota", new JsonPrimitive("11000"));
        return result.toString();
    }
}
