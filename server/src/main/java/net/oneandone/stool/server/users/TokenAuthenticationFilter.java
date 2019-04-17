package net.oneandone.stool.server.users;

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
    private final boolean auth;
    private final UserManager manager;

    public TokenAuthenticationFilter(boolean auth, UserManager manager) {
        this.auth = auth;
        this.manager = manager;
    }
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String token;
        User user;

        token = ((HttpServletRequest) request).getHeader("X-authentication");
        if (auth) {
            if (token == null) {
                throw new IOException("403 - authentication required"); // TODO
            }
            user = manager.authentication(token);
        } else {
            if (token != null) {
                throw new IOException("400 - use service authenticated"); // TODO
            }
            user = User.ANONYMOUS;
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
        chain.doFilter(request, response);
    }
}
