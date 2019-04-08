package net.oneandone.stool.server.cli;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class ApiLogging implements HandlerInterceptor {
    public ApiLogging() {
    }

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        System.out.println("preHandle " + request.getRequestURI() + " " + request.getHeader("X-stool-client-context"));
        return true;
    }

    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        System.out.println("afterCompletion " + request.getRequestURI());
    }
}
