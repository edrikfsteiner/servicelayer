package com.migration.servicelayer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.servicelayer.service.RateLimitService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RETRY_AFTER_HEADER = "X-Rate-Limit-Retry-After-Seconds";

    private final RateLimitService service;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/ingest");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            filterChain.doFilter(request, response);
            return;
        }

        String tenantId = (String) jwtAuth.getTokenAttributes().get("tenantId");

        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = service.resolveBucket(tenantId);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            String message = String.format("Rate limit exceeded for tenant '%s'. Retry after %d seconds.", tenantId, retryAfterSeconds);

            log.warn(message);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader(RETRY_AFTER_HEADER, String.valueOf(retryAfterSeconds));
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "error", "Too Many Requests",
                    "message", message
            ));
        }
    }
}
