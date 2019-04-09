package net.oneandone.stool.server.cli;

import net.oneandone.stool.server.util.Session;
import net.oneandone.sushi.fs.MkdirException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class ApiLogging implements HandlerInterceptor {
    private final Session session;

    @Autowired
    public ApiLogging(Session session) {
        this.session = session;
        System.out.println("apiLogging" + session);
    }

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws MkdirException {
        final String prefix = "/api/stages/";
        String uri;
        String stage;
        int idx;

        uri = request.getRequestURI();
        System.out.println("uri: " + uri);
        if (uri.startsWith(prefix)) {
            stage = uri.substring(prefix.length());
            idx = stage.indexOf('/');
            if (idx != -1) {
                stage = stage.substring(0, idx);
            }
        } else {
            stage = "none";
        }
        System.out.println("stage " + stage);
        session.logging.openStage(stage);
        session.logging.command(request.getHeader("X-stool-client-context"));
        return true;
    }

    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        session.logging.closeStage();
    }
}
