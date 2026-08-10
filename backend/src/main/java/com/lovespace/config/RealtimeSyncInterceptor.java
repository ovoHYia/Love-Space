package com.lovespace.config;

import com.lovespace.security.SessionPrincipal;
import com.lovespace.service.RealtimeSyncService;
import java.util.List;
import java.util.Locale;
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
        sync.publishAfterCommit(principal.coupleId(), principal.userId(),
                request.getHeader("X-Love-Client-Id"), request.getMethod(), resourcesOf(request.getRequestURI()));
    }

    private boolean isMutation(HttpServletRequest request) {
        return switch (request.getMethod()) {
            case "POST", "PUT", "PATCH", "DELETE" -> request.getRequestURI().startsWith("/api/");
            default -> false;
        };
    }

    static List<String> resourcesOf(String path) {
        String value = path.startsWith("/api/") ? path.substring(5) : path;
        String[] parts = value.split("/");
        if (parts.length == 0 || parts[0].isBlank()) return List.of();
        String root = parts[0];
        if ("trash".equals(root) && parts.length >= 4
                && "restore".equalsIgnoreCase(parts[3])) {
            return List.of("trash", trashResource(parts[1]));
        }
        return List.of(root);
    }

    private static String trashResource(String rawType) {
        return switch (rawType.toUpperCase(Locale.ROOT)) {
            case "MEMORY" -> "memories";
            case "DIARY" -> "diaries";
            case "MESSAGE" -> "messages";
            case "ANNIVERSARY" -> "anniversaries";
            case "WISH" -> "wishes";
            case "CALENDAR_EVENT" -> "calendar";
            default -> "trash";
        };
    }
}
