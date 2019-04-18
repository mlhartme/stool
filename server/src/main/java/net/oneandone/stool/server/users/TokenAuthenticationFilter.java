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

/** Make sure there's always a user set - */
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
            Server.LOGGER.debug(((HttpServletRequest) request).getRequestURI() + ": already authenticated: " + SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        } else {
            token = ((HttpServletRequest) request).getHeader("X-authentication");
            if (token == null) {
                ((HttpServletResponse) response).sendError(401, "authentication required: " + ((HttpServletRequest) request).getRequestURI());
                return;
            }
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


        chain.doFilter(request, response);
    }
}
