package com.migration.servicelayer.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.servicelayer.service.RateLimitingService;
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

/**
 * Intercepts every request to /api/ingest/** and enforces per-tenant rate limiting.
 *
 * Placement: runs inside the Spring Security filter chain, after
 * BearerTokenAuthenticationFilter, so the SecurityContext is already populated
 * with the authenticated JWT principal.
 *
 * tenantId is always read from the JWT claim — never from the request body or headers.
 *
 * On limit exhaustion: returns HTTP 429 with a JSON error body and the
 * X-Rate-Limit-Retry-After-Seconds response header.
 *
 * NOTE: This bean is NOT auto-registered as a raw servlet filter.
 * RateLimitConfig.rateLimitFilterRegistration() disables that registration so the
 * filter runs exclusively inside the security chain to avoid double execution.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RETRY_AFTER_HEADER = "X-Rate-Limit-Retry-After-Seconds";

    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/ingest");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

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

        Bucket bucket = rateLimitingService.resolveBucket(tenantId);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            log.warn("Rate limit exceeded for tenant '{}'. Retry after {} s.", tenantId, retryAfterSeconds);

            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader(RETRY_AFTER_HEADER, String.valueOf(retryAfterSeconds));
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "error", "Too Many Requests",
                    "message", "Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds."
            ));
        }
    }
}
