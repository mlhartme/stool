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
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.sushi.fs.NodeNotFoundException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/ui/stages")
public class StageController {
    @Autowired
    private World world;

    @Autowired
    private Server server;

    @Autowired
    private FileNode logs;

    @Autowired
    private FileNode jar;

    private final Collection<Stage> stagesCache;
    private long lastCacheRenew;

    public StageController() {
        stagesCache = new ArrayList<>();
        lastCacheRenew = 0L;
    }

    private Collection<Stage> stages(Server session) throws IOException {
        List<Stage> lst;

        if (System.currentTimeMillis() - lastCacheRenew > 4000) {
            stagesCache.clear();
            lst = session.listAll();
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
            stagesCache.addAll(lst);
            lastCacheRenew = System.currentTimeMillis();
        }
        return stagesCache;
    }

    @RequestMapping(method = RequestMethod.GET)
    public Collection<Stage> stages() throws IOException {
        return stages(server);
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView stagesAsHtml(ModelAndView modelAndView) throws IOException {
        modelAndView.setViewName("stages");
        modelAndView.addObject("userManager", server.userManager);
        modelAndView.addObject("stages", stages(server));

        return modelAndView;
    }

    @RequestMapping(value = "/{name}/logs", method = RequestMethod.GET)
    public ModelAndView logs(ModelAndView modelAndView, @PathVariable(value = "name") String stageName) throws Exception {
        modelAndView.setViewName("logs");
        modelAndView.addObject("logs", new Logs(resolveStage(stageName).getDirectory().join("logs")));
        return modelAndView;
    }


    @RequestMapping(value = "/{name}/logs/{log}", method = RequestMethod.GET)
    public ResponseEntity<Resource> log(@PathVariable(value = "name") String stageName, @PathVariable(value = "log") String path) throws Exception {
        Stage stage;
        Resource resource;

        stage = resolveStage(stageName);
        try {
            resource = new FileSystemResource(new Logs(stage.getDirectory().join("logs")).file(path));
            return new ResponseEntity<>(resource, HttpStatus.OK);
        } catch (NodeNotFoundException e) {
            throw new ResourceNotFoundException();
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionExport> handleApiException(HttpServletRequest request, Throwable e) {
        // TODO: really report this? maybe it's just a 404 ...
        server.reportException((String) request.getAttribute("command"), "StageController.handleApiException", e);
        return new ResponseEntity<>(new ExceptionExport(e), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Stage resolveStage(String stageName) throws ResourceNotFoundException {
        try {
            return server.load(stageName);
        } catch (IOException e) {
            throw (ResourceNotFoundException) new ResourceNotFoundException().initCause(e);
        }
    }

    private static class ExceptionExport {
        private final String message;
        ExceptionExport(Throwable e) {
            message = e.getMessage();
        }

        private String getMessage() {
            return message;
        }
    }
}
