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
package net.oneandone.stool.server.api;

import net.oneandone.stool.server.users.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class ApiLogging implements HandlerInterceptor {
    public static final String CLIENT_INVOCATION = "client-invocation";
    public static final String CLIENT_COMMAND = "client-command";
    public static final String USER = "user";
    public static final String STAGE = "stage";
    public static final String REQUEST = "request";

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        final String prefix = "/api/stages/";
        String uri;
        String stage;
        int idx;

        uri = request.getRequestURI();
        if (uri.startsWith(prefix)) {
            stage = uri.substring(prefix.length());
            idx = stage.indexOf('/');
            if (idx != -1) {
                stage = stage.substring(0, idx);
            }
        } else {
            stage = "none";
        }
        MDC.put(CLIENT_INVOCATION, request.getHeader("X-stool-client-invocation"));
        MDC.put(CLIENT_COMMAND, request.getHeader("X-stool-client-command"));
        MDC.put(REQUEST, request.getMethod() + " \"" + uri + '"');
        MDC.put(USER, User.authenticatedOrAnonymous().login);
        MDC.put(STAGE, stage);

        return true;
    }

    private static final Logger ACCESS = LoggerFactory.getLogger("ACCESS");

    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        ACCESS.info(Integer.toString(response.getStatus()));
        MDC.remove(CLIENT_INVOCATION);
        MDC.remove(CLIENT_COMMAND);
        MDC.remove(REQUEST);
        MDC.remove(USER);
        MDC.remove(STAGE);
    }
}
