/*
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
package net.oneandone.stool.server.ui;

import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/ui/stages")
public class StageController {
    @Autowired
    private Server server;

    private final Engine engine;

    public StageController() throws IOException {
        engine = Engine.create(); // TODO
    }

    private Collection<Stage> list() throws IOException {
        List<Stage> lst;

        lst = server.listAll();
        Collections.sort(lst, new Comparator<Stage>() {
            @Override
            public int compare(Stage left, Stage right) {
                boolean lr;
                boolean rr;

                lr = left.configuration.expire.isReserved();
                rr = right.configuration.expire.isReserved();
                if (lr == rr) {
                    return left.getName().compareToIgnoreCase(right.getName());
                } else {
                    return lr ? -1 : 1;
                }
            }
        });
        return lst;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ModelAndView stages(ModelAndView modelAndView) throws IOException {
        modelAndView.setViewName("stages");
        modelAndView.addObject("engine", engine);
        modelAndView.addObject("pool", server.pool);
        modelAndView.addObject("userManager", server.userManager);
        modelAndView.addObject("stages", list());

        return modelAndView;
    }
}
