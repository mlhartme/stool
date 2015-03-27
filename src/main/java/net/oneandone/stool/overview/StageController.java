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
package net.oneandone.stool.overview;

import net.oneandone.maven.embedded.Maven;
import net.oneandone.stool.EnumerationFailed;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.users.Users;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.CreateOutputStreamException;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.NodeNotFoundException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import org.xml.sax.SAXException;

import javax.naming.NamingException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/stages")
public class StageController {
    private static final Logger LOG = LoggerFactory.getLogger(StageController.class);

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
    private Stages stages;

    @RequestMapping(method = RequestMethod.GET)
    public Collection<StageInfo> stages()
            throws IOException, URISyntaxException, SAXException, NamingException, UserNotFound, EnumerationFailed {
        return stages.load(session, users);
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView stagesAsHtml(ModelAndView modelAndView)
            throws SAXException, NamingException, UserNotFound, IOException, EnumerationFailed {
        modelAndView.setViewName("stages");
        modelAndView.addObject("stages", stages.load(session, users));

        return modelAndView;
    }


    @RequestMapping(value = "{name}", method = RequestMethod.GET)
    public ResponseEntity stage(@PathVariable(value = "name") String stageName) throws Exception {
        Stage stage;
        stage = resolveStage(stageName);

        return new ResponseEntity<>(StageInfo.fromStage(stage, users), HttpStatus.OK);
    }

    @RequestMapping(value = "{name}", method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView stageAsHtml(@PathVariable(value = "name") String stageName, ModelAndView modelAndView) throws Exception {
        List<StageInfo> stageInfos = new ArrayList<>();
        modelAndView.setViewName("stages");
        for (StageInfo stageInfo : stages.load(session, users)) {
            if (stageName.equals(stageInfo.name)) {
                stageInfos.add(stageInfo);
            }
        }
        modelAndView.addObject("stages", stageInfos);

        return modelAndView;
    }

    @RequestMapping(value = "/{name}/logs/", method = RequestMethod.GET)
    public ResponseEntity logs(@PathVariable(value = "name") String stageName) throws Exception {
        Stage stage;
        Logs logs;

        stage = resolveStage(stageName);
        logs = new Logs();
        logs.addAll(stage.logs().list(), stageName);
        return new ResponseEntity<>(logs, HttpStatus.OK);
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
    public String execute(@PathVariable(value = "name") String stageName, @PathVariable(value = "action") String action)
      throws Exception {
        return execute(stageName, action, null);
    }

    @RequestMapping(value = "{name}/{action}/{options}", method = RequestMethod.POST)
    public String start(@PathVariable(value = "name") String stageName, @PathVariable(value = "action") String action,
      @PathVariable(value = "options") String options) throws Exception {
        return execute(stageName, action, options);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionExport> handleApiException(Throwable e) {
        LOG.error("Exception in stageoverview", e);
        return new ResponseEntity<>(new ExceptionExport(e), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    //TODO
    private Stage resolveStage(String stageName) throws IOException {
        if (!session.stageNames().contains(stageName)) {
            throw new ResourceNotFoundException();
        }
        Stage stage = session.load(stageName);
        stage.setMaven(maven);
        return stage;
    }

    public Console console() throws FileNotFoundException, CreateOutputStreamException {
        return Console.create(world);
    }

    public String execute(String stage, String command, String options) throws IOException {
        String id;

        id = UUID.randomUUID().toString();
        executorService.submit(new StoolCallable(command, options, stage, SecurityContextHolder.getContext().getAuthentication().getName(), id, logs, session.user));
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
