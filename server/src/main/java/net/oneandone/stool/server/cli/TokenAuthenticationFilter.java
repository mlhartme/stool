package net.oneandone.stool.server.cli;

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
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String token;

        token = ((HttpServletRequest) request).getHeader("X-authentication");
        if (token != null) {
            SecurityContextHolder.getContext().setAuthentication(new Authentication() {
                @Override
                public Collection<? extends GrantedAuthority> getAuthorities() {
                    return Collections.emptyList();
                }

                @Override
                public Object getCredentials() {
                    return token;
                }

                @Override
                public Object getDetails() {
                    return "details";
                }

                @Override
                public Object getPrincipal() {
                    return "principal";
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
                    return "mhm-token";
                }
            });
        }
        chain.doFilter(request, response);
    }
}
