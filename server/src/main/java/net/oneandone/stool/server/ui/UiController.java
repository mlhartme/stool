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
import net.oneandone.stool.server.users.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ModelAndView;

import javax.mail.MessagingException;

@Controller
@Scope(WebApplicationContext.SCOPE_REQUEST)
@RequestMapping("/ui")
public class UiController {
    private static final Logger LOG = LoggerFactory.getLogger(UiController.class);

    @Autowired
    private Server server;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ModelAndView dashboard(ModelAndView modelAndView) {
        User user;

        user = User.authenticatedOrAnonymous();
        modelAndView.setViewName("index");
        modelAndView.addObject("username", user.name);
        LOG.info("[" + user + "] GET /");
        return modelAndView;
    }

    @RequestMapping(value = "whoami", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity whoAmI() {
        return new ResponseEntity<>(User.authenticatedOrAnonymous(), HttpStatus.OK);
    }

    // pages

    @RequestMapping(value = "feedback", method = RequestMethod.GET)
    public String feedback() {
        return "feedback";
    }

    @RequestMapping(value = "feedback", method = RequestMethod.POST)
    public ResponseEntity sendFeedback(@ModelAttribute("message") String message) throws MessagingException {
        String subject;

        if (message.isEmpty()) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        subject = "[Stool] Feedback from " + SecurityContextHolder.getContext().getAuthentication().getName();
        server.configuration.mailer().send(server.configuration.admin, new String[] { server.configuration.admin }, subject, message);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

}
