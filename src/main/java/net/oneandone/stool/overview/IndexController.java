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

import net.oneandone.stool.util.Mailer;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.World;
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
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

@Controller
@Scope(WebApplicationContext.SCOPE_REQUEST)
@RequestMapping("/")
public class IndexController {

    @Autowired
    private Session session;

    @Autowired
    private World world;


    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ModelAndView index(ModelAndView modelAndView) {
        InetOrgPerson inetOrgPerson = (InetOrgPerson) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        modelAndView.setViewName("index");
        modelAndView.addObject("username", inetOrgPerson.getDisplayName());
        return modelAndView;
    }

    @RequestMapping(value = "whoami", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity whoAmI() throws Exception {
        return new ResponseEntity<>(SecurityContextHolder.getContext().getAuthentication().getPrincipal(), HttpStatus.OK);
    }

    @RequestMapping(value = "configuration", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, String> configuration() {
        Map<String, String> model;

        model = new HashMap<>();
        for (Field field : session.configuration.getClass().getDeclaredFields()) {
            try {
                model.put(field.getName(), String.valueOf(field.get(session.configuration)));
            } catch (IllegalAccessException ignored) {
                //we're goin to ignore this. Because we change the format of the configuration. we wont need any private fields
            }
        }
        return model;
    }

    // pages

    @RequestMapping(value = "feedback", method = RequestMethod.GET)
    public String feedback() throws IllegalAccessException {
        return "feedback";
    }

    @RequestMapping(value = "settings", method = RequestMethod.GET)
    public String settings() throws IllegalAccessException {
        return "settings";
    }

    @RequestMapping(value = "statistics", method = RequestMethod.GET)
    public String statistics() throws IllegalAccessException {
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
                session.configuration.mailPassword).send(session.configuration.contactAdmin,
                new String[] { session.configuration.contactAdmin }, subject, message);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

}
