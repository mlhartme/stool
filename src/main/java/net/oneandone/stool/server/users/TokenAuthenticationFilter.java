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
package net.oneandone.stool.server.users;

import net.oneandone.stool.server.Server;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * Make sure there's always a user set -
 */
// TODO: watch https://www.youtube.com/watch?v=EeXFwR21J1A&list=PLEocw3gLFc8XRaRBZkhBEZ_R3tmvfkWZz&index=5
public class TokenAuthenticationFilter extends GenericFilterBean {
    private final UserManager manager;

    public TokenAuthenticationFilter(UserManager manager) {
        this.manager = manager;
    }
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String token;
        User user;

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            Server.LOGGER.debug(((HttpServletRequest) request).getRequestURI() + ": already authenticated: " + User.authenticatedOrAnonymous().login);
        } else {
            token = ((HttpServletRequest) request).getHeader("X-authentication");
            if (token != null) {
                user = manager.authentication(token);
                if (user == null) {
                    ((HttpServletResponse) response).sendError(401, "authentication failed");
                    return;
                }
                SecurityContextHolder.getContext().setAuthentication(new Authentication() {
                    @Override
                    public Collection<? extends GrantedAuthority> getAuthorities() {
                        return Collections.singleton(new GrantedAuthority() {
                            @Override
                            public String getAuthority() {
                                return "ROLE_LOGIN";
                            }
                        });
                    }

                    @Override
                    public Object getCredentials() {
                        return token;
                    }

                    @Override
                    public Object getDetails() {
                        return "token authentication";
                    }

                    @Override
                    public Object getPrincipal() {
                        return user;
                    }

                    @Override
                    public boolean isAuthenticated() {
                        return true;
                    }

                    @Override
                    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
                        throw new IllegalStateException();
                    }

                    @Override
                    public String getName() {
                        return user.name;
                    }
                });
            }
        }
        chain.doFilter(request, response);
    }
}
