package net.oneandone.stool.server.cli;

import net.oneandone.stool.server.users.User;
import net.oneandone.stool.server.util.TokenManager;
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

public class TokenAuthenticationFilter extends GenericFilterBean {
    private final TokenManager manager;

    public TokenAuthenticationFilter(TokenManager manager) {
        this.manager = manager;
    }
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String token;
        User user;

        token = ((HttpServletRequest) request).getHeader("X-authentication");
        if (token != null) {
            user = manager.authentication(token);
            if (user != null) {
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
