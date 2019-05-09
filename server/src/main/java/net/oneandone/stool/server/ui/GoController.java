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
import net.oneandone.sushi.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;

@Controller
@RequestMapping("/go")
public class GoController {
    @Autowired
    private Server session;

    // /stage/app
    @RequestMapping(value = "/**", method = RequestMethod.GET)
    public ModelAndView goToStage(HttpServletRequest httpServletRequest) throws IOException {
        String stageName;
        String appName;
        String baseurl;
        Stage stage;
        int idx;
        Map<String, String> urlMap;
        String url;

        stageName = Strings.removeLeft(httpServletRequest.getServletPath(), "/go/");
        idx = stageName.indexOf('/');
        if (idx == -1) {
            appName = null;
        } else {
            appName = stageName.substring(idx + 1);
            stageName = stageName.substring(0, idx);
        }
        baseurl = httpServletRequest.getRequestURL().toString();
        baseurl = baseurl.substring(0, baseurl.indexOf('/', 8));
        if (!session.stageNames().contains(stageName)) {
            return new ModelAndView("redirect:" + baseurl + "/#!404:" + stageName);
        }
        stage = session.load(stageName);
        if (!stage.dockerRunningContainerList().isEmpty()) {
            urlMap = stage.urlMap(null);
            if (appName == null) {
                url = urlMap.values().iterator().next();
            } else {
                url = urlMap.get(appName);
            }
            if (url == null) {
                return new ModelAndView("redirect:" + baseurl + "/#!404:" + stageName + "/" + appName);
            } else {
                return new ModelAndView("redirect:" + url);
            }
        } else {
            return new ModelAndView("redirect:" + baseurl + "/#!500!" + stageName + "!down");
        }
    }
}
