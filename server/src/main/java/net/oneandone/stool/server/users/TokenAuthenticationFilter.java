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
            Server.LOGGER.debug("already authenticated: " + SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                    + " " + ((HttpServletRequest) request).getRequestURI());
        } else {
            token = ((HttpServletRequest) request).getHeader("X-authentication");
            if (token == null) {
                throw new IOException("403 - authentication required: " + ((HttpServletRequest) request).getRequestURI()); // TODO
            }
            user = manager.authentication(token);
            if (user == null) {
                throw new IOException("403 - authentication failed"); // TODO
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
