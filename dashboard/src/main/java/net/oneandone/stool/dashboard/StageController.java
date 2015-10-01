/**
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

import net.oneandone.maven.embedded.Maven;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.Users;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.NodeNotFoundException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
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
    private ExecutorService executorService;

    @Autowired
    private Maven maven;

    @Autowired
    private StageInfoCache stages;

    @RequestMapping(method = RequestMethod.GET)
    public Collection<StageInfo> stages() throws IOException {
        return stages.get(logs, session, users);
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView stagesAsHtml(ModelAndView modelAndView) throws IOException {
        modelAndView.setViewName("stages");
        modelAndView.addObject("stages", stages.get(logs, session, users));

        return modelAndView;
    }


    @RequestMapping(value = "{name}", method = RequestMethod.GET)
    public ResponseEntity stage(@PathVariable(value = "name") String stageName) throws Exception {
        Stage stage;

        stage = resolveStage(stageName);
        return new ResponseEntity<>(StageInfo.fromStage(logs, stage, users), HttpStatus.OK);
    }

    @RequestMapping(value = "{name}", method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView stageAsHtml(@PathVariable(value = "name") String stageName, ModelAndView modelAndView) throws Exception {
        List<StageInfo> stageInfos = new ArrayList<>();
        modelAndView.setViewName("stages");
        for (StageInfo stageInfo : stages.get(logs, session, users)) {
            if (stageName.equals(stageInfo.name)) {
                stageInfos.add(stageInfo);
            }
        }
        modelAndView.addObject("stages", stageInfos);

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
    public String action(@PathVariable(value = "name") String stageName, @PathVariable(value = "action") String action)
      throws Exception {
        return execute(stageName, action);
    }

    @RequestMapping(value = "{name}/{action}/{option1}", method = RequestMethod.POST)
    public String action(@PathVariable(value = "name") String stageName, @PathVariable(value = "action") String action,
        @PathVariable(value = "option1") String option1) throws Exception {
           return execute(stageName, action, option1);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionExport> handleApiException(Throwable e) {
        // TODO: really report this? maybe it's just a 404 ...
        session.reportException("StageController.handleApiException", e);
        return new ResponseEntity<>(new ExceptionExport(e), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Stage resolveStage(String stageName) throws ResourceNotFoundException {
        Stage stage;

        try {
            stage = session.load(stageName);
        } catch (IOException e) {
            throw new ResourceNotFoundException();
        }
        stage.setMaven(maven);
        return stage;
    }

    public Console console() {
        return Console.create(world);
    }

    public String execute(String stage, String command, String ... options) throws IOException {
        String id;

        id = UUID.randomUUID().toString();
        executorService.submit(StoolCallable.create(id, logs, resolveStage(stage), command, options));
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
