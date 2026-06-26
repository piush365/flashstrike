package com.example.flashsale.domain;

/**
 * All possible outcomes of a flash-sale order attempt.
 * The controller maps each value to the appropriate HTTP status.
 */
public enum OrderResult {

    /** Inventory decremented and order event published to Kafka. */
    ACCEPTED,

    /** Stock counter reached zero; no inventory was decremented. */
    SOLD_OUT,

    /** User has exceeded their per-minute request quota. */
    RATE_LIMITED,

    /** A concurrent request from the same user is already in-flight. */
    CONCURRENT_BLOCK,

    /** A circuit breaker is open; an upstream dependency is unavailable. */
    CIRCUIT_OPEN,

    /** An unexpected error occurred (Kafka timeout, Redis error, interruption). */
    ERROR
}
