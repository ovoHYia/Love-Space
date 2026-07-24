package com.lovespace.config;

import com.lovespace.security.SessionPrincipal;
import com.lovespace.service.RealtimeSyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RealtimeSyncInterceptor implements HandlerInterceptor {
    private static final String PRINCIPAL_ATTRIBUTE = RealtimeSyncInterceptor.class.getName() + ".principal";
    private final RealtimeSyncService sync;

    public RealtimeSyncInterceptor(RealtimeSyncService sync) { this.sync = sync; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isMutation(request)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof SessionPrincipal principal) {
                request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (ex != null || response.getStatus() >= 400 || !isMutation(request)) return;
        Object value = request.getAttribute(PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal principal)) return;
        sync.publish(principal.coupleId(), principal.userId(), request.getHeader("X-Love-Client-Id"),
                request.getMethod(), resourceOf(request.getRequestURI()));
    }

    private boolean isMutation(HttpServletRequest request) {
        return switch (request.getMethod()) {
            case "POST", "PUT", "PATCH", "DELETE" -> request.getRequestURI().startsWith("/api/");
            default -> false;
        };
    }

    private String resourceOf(String path) {
        String value = path.startsWith("/api/") ? path.substring(5) : path;
        int slash = value.indexOf('/');
        return slash < 0 ? value : value.substring(0, slash);
    }
}
