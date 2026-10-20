package com.bidv.coretraining.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(CORRELATION_ID_HEADER));
        long startedAt = System.nanoTime();
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(CORRELATION_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            String username = resolveUsername();
            int status = response.getStatus();
            String message = "{} {} status={} durationMs={} user={} remote={}";
            Object[] arguments = {
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    durationMs,
                    username,
                    request.getRemoteAddr()
            };

            if (status >= 500) {
                log.error(message, arguments);
            } else if (status >= 400) {
                log.warn(message, arguments);
            } else {
                log.info(message, arguments);
            }
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String resolveTraceId(String requestedTraceId) {
        if (StringUtils.hasText(requestedTraceId)
                && SAFE_CORRELATION_ID.matcher(requestedTraceId).matches()) {
            return requestedTraceId;
        }
        return UUID.randomUUID().toString();
    }

    private String resolveUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                ? authentication.getName()
                : "anonymous";
    }
}
