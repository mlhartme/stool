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

import net.oneandone.stool.core.Server;
import net.oneandone.stool.server.users.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ModelAndView;

import javax.mail.MessagingException;

@Controller
@Scope(WebApplicationContext.SCOPE_REQUEST)
@RequestMapping("/ui")
public class DashboardController {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardController.class);

    @Autowired
    private Server server;

    @GetMapping("dashboard")
    public ModelAndView dashboard(ModelAndView modelAndView) {
        User user;

        user = User.authenticatedOrAnonymous();
        modelAndView.setViewName("dashboard");
        modelAndView.addObject("version", server.version);
        modelAndView.addObject("username", user.name);
        LOG.info("[" + user.login + "] GET dashboard");
        return modelAndView;
    }

    @GetMapping("feedback")
    public String feedback() {
        return "feedback";
    }

    @PostMapping("feedback")
    public ResponseEntity sendFeedback(@ModelAttribute("message") String message) throws MessagingException {
        String subject;

        if (message.isEmpty()) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        subject = "[Stool] Feedback from " + SecurityContextHolder.getContext().getAuthentication().getName();
        server.settings.mailer().send(server.settings.admin, new String[] { server.settings.admin }, subject, message);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

}
