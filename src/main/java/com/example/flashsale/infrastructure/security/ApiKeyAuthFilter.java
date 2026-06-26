package com.example.flashsale.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Stateless API-key authentication filter.
 *
 * <p>Reads the {@code X-API-Key} request header and rejects any request whose key does
 * not match the configured value with a minimal 401 response. No session is created.
 *
 * <p>In a production multi-tenant system this would be replaced by JWT / OAuth 2.0,
 * but API-key authentication is appropriate for service-to-service calls where the
 * caller is a known internal system (e.g. an API gateway forwarding requests).
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final String expectedApiKey;

    public ApiKeyAuthFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String providedKey = request.getHeader(API_KEY_HEADER);

        if (expectedApiKey.equals(providedKey)) {
            var auth = new UsernamePasswordAuthenticationToken("api-client", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } else {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"https://flashsale.dev/problems/unauthorized",\
                    "title":"Unauthorized","detail":"Missing or invalid X-API-Key header"}""");
        }
    }
}
