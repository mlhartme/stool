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

public class AuthenticationFilter extends GenericFilterBean {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        System.out.println("tokenFilter");
        if (((HttpServletRequest) request).getHeader("X-mhm") != null) {
            System.out.println("auth");
            SecurityContextHolder.getContext().setAuthentication(new Authentication() {
                @Override
                public Collection<? extends GrantedAuthority> getAuthorities() {
                    return Collections.singleton(new GrantedAuthority() {
                        @Override
                        public String getAuthority() {
                            return "LOGIN";
                        }
                    });
                }

                @Override
                public Object getCredentials() {
                    return "creads";
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
