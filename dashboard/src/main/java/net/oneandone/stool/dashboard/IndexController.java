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

import net.oneandone.stool.server.configuration.Accessor;
import net.oneandone.stool.server.configuration.StoolConfiguration;
import net.oneandone.stool.server.util.Mailer;
import net.oneandone.stool.server.util.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.ldap.userdetails.InetOrgPerson;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ModelAndView;

import javax.mail.MessagingException;
import javax.servlet.ServletRequest;
import java.util.HashMap;
import java.util.Map;

@Controller
@Scope(WebApplicationContext.SCOPE_REQUEST)
@RequestMapping("/")
public class IndexController {
    private static final Logger LOG = LoggerFactory.getLogger(IndexController.class);

    @Autowired
    private Server session;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ModelAndView index(ModelAndView modelAndView) {
        Object username;

        username = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (username instanceof InetOrgPerson) {
            username = ((InetOrgPerson) username).getDisplayName();
        }
        modelAndView.setViewName("index");
        modelAndView.addObject("username", username);
        LOG.info("[" + username + "] GET /");
        return modelAndView;
    }

    @RequestMapping(value = "whoami", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity whoAmI() {
        return new ResponseEntity<>(SecurityContextHolder.getContext().getAuthentication().getPrincipal(), HttpStatus.OK);
    }

    @RequestMapping(value = "configuration", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, String> configuration() {
        Map<String, String> model;

        model = new HashMap<>();
        for (Map.Entry<String, Accessor> entry : StoolConfiguration.properties().entrySet()) {
            model.put(entry.getKey(), entry.getValue().get(session.configuration));
        }
        return model;
    }

    // pages

    @RequestMapping(value = "feedback", method = RequestMethod.GET)
    public String feedback() {
        return "feedback";
    }

    @RequestMapping(value = "settings", method = RequestMethod.GET)
    public String settings() {
        return "settings";
    }

    @RequestMapping(value = "statistics", method = RequestMethod.GET)
    public String statistics() {
        return "statistics";
    }

    @RequestMapping(value = "feedback", method = RequestMethod.POST)
    public ResponseEntity sendFeedback(@ModelAttribute("message") String message, ServletRequest request) throws MessagingException {
        String subject;
        if (message.isEmpty()) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        subject = "[Stool] Feedback from " + SecurityContextHolder.getContext().getAuthentication().getName();
        new Mailer(session.configuration.mailHost, session.configuration.mailUsername,
                session.configuration.mailPassword).send(session.configuration.admin,
                new String[] { session.configuration.admin }, subject, message);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

}
