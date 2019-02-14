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
package net.oneandone.stool.dashboard;

import net.oneandone.stool.stage.Project;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.Users;
import net.oneandone.stool.util.Session;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/stages")
public class StageController {
    @Autowired
    private Users users;

    @Autowired
    private World world;

    @Autowired
    private Session session;

    @Autowired
    private FileNode logs;

    @Autowired
    private FileNode jar;

    @Autowired
    private ExecutorService executorService;

    private final Collection<Stage> stagesCache;
    private long lastCacheRenew;

    public StageController() {
        stagesCache = new ArrayList<>();
        lastCacheRenew = 0L;
    }

    private Collection<Stage> stages(Session session) throws IOException {
        List<Stage> lst;

        if (System.currentTimeMillis() - lastCacheRenew > 4000) {
            stagesCache.clear();
            session.wipeStaleBackstages();
            session.updatePool();
            lst = session.listAll();
            Collections.sort(lst, new Comparator<Stage>() {
                @Override
                public int compare(Stage left, Stage right) {
                    boolean lr;
                    boolean rr;

                    lr = left.config().expire.isReserved();
                    rr = right.config().expire.isReserved();
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
        return stages(session);
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView stagesAsHtml(ModelAndView modelAndView) throws IOException {
        modelAndView.setViewName("stages");
        modelAndView.addObject("users", users);
        modelAndView.addObject("stages", stages(session));

        return modelAndView;
    }

    @RequestMapping(value = "/{name}/logs", method = RequestMethod.GET)
    public Map<String, String> logs(HttpServletRequest httpServletRequest, @PathVariable(value = "name") String stageName) throws Exception {
        Stage stage;
        String baseUrl;

        stage = resolveStage(stageName);
        baseUrl = httpServletRequest.getRequestURL().toString();
        baseUrl = baseUrl.substring(0, baseUrl.indexOf('/', 8) + 1);
        return stage.logs().list(baseUrl + "stages/" + stageName + "/logs/");
    }

    @RequestMapping(value = "/{name}/logs/{log}", method = RequestMethod.GET)
    public ResponseEntity<Resource> log(@PathVariable(value = "name") String stageName,
      @PathVariable(value = "log") String log) throws Exception {
        Stage stage;
        String logfile;

        stage = resolveStage(stageName);
        if (log.endsWith(".log")) {
            logfile = log;
        } else {
            logfile = log + ".log";
        }

        try {
            Resource resource;
            resource = new FileSystemResource(stage.logs().file(logfile));

            return new ResponseEntity<>(resource, HttpStatus.OK);

        } catch (NodeNotFoundException e) {
            throw new ResourceNotFoundException();
        }
    }

    @RequestMapping(value = "{name}/{action}", method = RequestMethod.POST)
    public String action(@PathVariable(value = "name") String stageName, @PathVariable(value = "action") String action) {
        return execute(stageName, action);
    }

    @RequestMapping(value = "{name}/{action}/{arguments}", method = RequestMethod.POST)
    public String action(@PathVariable(value = "name") String stageName, @PathVariable(value = "action") String action,
        @PathVariable(value = "arguments") String arguments) {
        return execute(stageName, action, Strings.toArray(Separator.COMMA.split(arguments)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionExport> handleApiException(Throwable e) {
        // TODO: really report this? maybe it's just a 404 ...
        session.reportException("StageController.handleApiException", e);
        return new ResponseEntity<>(new ExceptionExport(e), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Stage resolveStage(String stageName) throws ResourceNotFoundException {
        Project project;

        try {
            project = session.loadByName(stageName);
        } catch (IOException e) {
            throw (ResourceNotFoundException) new ResourceNotFoundException().initCause(e);
        }
        return project.getStage();
    }

    public String execute(String stage, String command, String ... arguments) {
        String id;

        id = UUID.randomUUID().toString();
        executorService.submit(StoolCallable.create(jar, session.home, id, logs, resolveStage(stage),
                session.logging.getUser(), command, arguments));
        return id;
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
