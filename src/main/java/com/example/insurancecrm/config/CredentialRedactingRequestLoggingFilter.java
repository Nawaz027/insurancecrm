package com.example.insurancecrm.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * CommonsRequestLoggingFilter logs the full request body at DEBUG. That's fine for most
 * endpoints, but /api/auth/** carries raw passwords/refresh tokens, and creating/updating a user
 * carries a raw password too — none of that belongs in logs, so those routes are skipped
 * entirely rather than logged with a redacted body.
 *
 * A named class (rather than an inline subclass) matters here: GenericFilterBean logs via
 * {@code LogFactory.getLog(getClass())}, so an anonymous subclass would silently change the
 * logger name away from what {@code logging.level} in application.yaml expects, turning off
 * request logging entirely rather than just for these routes.
 */
public class CredentialRedactingRequestLoggingFilter extends CommonsRequestLoggingFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return carriesCredentials(request) || super.shouldNotFilter(request);
    }

    private boolean carriesCredentials(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (uri.startsWith("/api/auth/")) return true;
        if ("POST".equals(method) && uri.equals("/api/users")) return true;
        return "PUT".equals(method) && uri.matches("/api/users/[^/]+");
    }
}
