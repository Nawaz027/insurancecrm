package com.example.insurancecrm.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Runs whenever a request reaches a protected endpoint without a valid authenticated
 * principal (missing, malformed, or expired token). Returns 401 in the app's standard
 * ApiResponse envelope shape, so the frontend can distinguish "please log in again" from a
 * genuine 403 authorization failure (@PreAuthorize role checks, handled separately by
 * GlobalExceptionHandler). Written by hand rather than via an injected ObjectMapper —
 * this class is instantiated very early during security filter chain construction,
 * before Jackson auto-configuration beans are reliably available.
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body = """
                {"success":false,"message":"Authentication required — please log in again","timestamp":"%s"}"""
                .formatted(LocalDateTime.now());
        response.getWriter().write(body);
    }
}
