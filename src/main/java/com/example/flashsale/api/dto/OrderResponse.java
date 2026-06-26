package com.example.flashsale.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Uniform JSON response envelope for every order endpoint.
 * Keeps the API contract stable even as internal result types evolve.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        String status,
        String message,
        String correlationId,
        Instant timestamp
) {
    public static OrderResponse of(String status, String message, String correlationId) {
        return new OrderResponse(status, message, correlationId, Instant.now());
    }
}
