package net.oneandone.stool.server.cli;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class RestController {
    @GetMapping("/version") @ResponseBody
    public String version() {
        return "<html><title>version</title></html>";
    }
}
