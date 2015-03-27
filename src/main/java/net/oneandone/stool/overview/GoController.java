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

import net.oneandone.stool.EnumerationFailed;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.users.Users;
import net.oneandone.stool.util.Predicate;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.World;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.xml.sax.SAXException;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/go")
public class GoController {
    @Autowired
    private World world;
    @Autowired
    private Users users;

    @Autowired
    private Session session;

    @RequestMapping(value = "/**", method = RequestMethod.GET)
    public ModelAndView goToStage(HttpServletRequest httpServletRequest)
            throws IOException, SAXException, NamingException, UserNotFound, EnumerationFailed {
        final String requestetStage = httpServletRequest.getServletPath().replace("/go/", "");
        String baseurl = httpServletRequest.getRequestURL().toString();
        baseurl = baseurl.substring(0, baseurl.indexOf('/', 8));

        if (!session.wrappers.join(requestetStage).exists()) {
            return new ModelAndView("redirect:" + baseurl + "/#!404:" + requestetStage);
        }

        List<Stage> stages = StageGatherer.doList(session, new Predicate() {
            @Override
            public boolean matches(Stage stage1) {
                return stage1.getName().equals(requestetStage);
            }
        });
        StageInfo stage = StageInfo.fromStage(stages.get(0), users);

        switch (stage.getRunning()) {
            case "up":
                return new ModelAndView("redirect:" + stage.getUrls().values().toArray()[0]);
            default:
                return new ModelAndView("redirect:" + baseurl + "/#!500!" + stage.getName() + "!" + stage.getRunning());
        }
    }
}
