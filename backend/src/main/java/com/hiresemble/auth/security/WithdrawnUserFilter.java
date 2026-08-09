package com.hiresemble.auth.security;

import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.security.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class WithdrawnUserFilter extends OncePerRequestFilter {

    private final JdbcClient jdbc;
    private final SecurityErrorResponseWriter errorWriter;

    public WithdrawnUserFilter(JdbcClient jdbc, SecurityErrorResponseWriter errorWriter) {
        this.jdbc = jdbc;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            long active = jdbc.sql("SELECT count(*) FROM users WHERE id=:id AND status='ACTIVE'")
                    .param("id", user.id())
                    .query(Long.class)
                    .single();
            if (active != 1) {
                if (request.getSession(false) != null) {
                    try {
                        request.getSession(false).invalidate();
                    } catch (IllegalStateException ignored) {
                        // Already invalidated by a concurrent withdrawal request.
                    }
                }
                SecurityContextHolder.clearContext();
                errorWriter.write(request, response, ErrorCode.AUTHENTICATION_REQUIRED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
