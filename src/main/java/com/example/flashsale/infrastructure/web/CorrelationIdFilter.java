package com.example.flashsale.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Injects a correlation ID into every request, making it possible to trace
 * a single order across controller → service → Kafka → consumer → database.
 *
 * <ul>
 *   <li>Reads {@code X-Correlation-Id} from the inbound header when provided
 *       (allows an upstream gateway to propagate its own trace ID).</li>
 *   <li>Generates a UUID when the header is absent.</li>
 *   <li>Writes the ID into SLF4J {@link MDC} so it appears in every log line.</li>
 *   <li>Echoes the ID back in the response header so callers can correlate.</li>
 *   <li>Stores the ID in a request attribute so controllers can embed it in responses.</li>
 * </ul>
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER      = "X-Correlation-Id";
    public static final String MDC_KEY     = "correlationId";
    public static final String REQUEST_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = Optional.ofNullable(request.getHeader(HEADER))
                .filter(v -> !v.isBlank())
                .orElse(UUID.randomUUID().toString());

        MDC.put(MDC_KEY, correlationId);
        request.setAttribute(REQUEST_KEY, correlationId);
        response.setHeader(HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
