package com.example.flashsale.infrastructure.redis;

/**
 * Port (in hexagonal architecture terms) for inventory stock operations.
 * Keeps FlashSaleService free of Redis-specific types, making it unit-testable
 * against a plain interface mock without ByteBuddy class instrumentation.
 */
public interface InventoryStore {

    /**
     * Atomically decrements stock by 1 if it is > 0.
     *
     * @return the new stock level after decrement, or -1 if stock was already 0 or negative
     */
    long decrementIfPositive(String productKey);

    /**
     * Increments stock by 1. Used to roll back a decrement when Kafka send fails.
     */
    void increment(String productKey);
}
