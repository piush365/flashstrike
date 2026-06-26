package com.example.flashsale.application;

import com.example.flashsale.domain.OrderEvent;
import com.example.flashsale.domain.OrderResult;
import com.example.flashsale.infrastructure.config.FlashSaleProperties;
import com.example.flashsale.infrastructure.redis.InventoryStore;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RLock;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Orchestrates the flash-sale order flow:
 * <ol>
 *   <li>Per-user rate limiting (Redis Redisson RRateLimiter)</li>
 *   <li>Per-user distributed lock (Redisson RLock) — prevents double-orders</li>
 *   <li>Atomic inventory decrement (Redis Lua script)</li>
 *   <li>Event publication to Kafka (with rollback on failure)</li>
 * </ol>
 *
 * A Resilience4j circuit breaker named {@code "kafka"} protects the Kafka send path.
 * When the circuit opens (due to repeated failures), orders fast-fail with
 * {@link OrderResult#CIRCUIT_OPEN} instead of blocking threads for 5 seconds each.
 */
@Service
public class FlashSaleService {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleService.class);

    private static final String STOCK_KEY_PREFIX        = "stock:product:";
    private static final String RATE_LIMITER_KEY_PREFIX = "rate_limiter:";
    private static final String LOCK_KEY_PREFIX         = "lock:order:";
    private static final String CB_KAFKA                = "kafka";

    private static final long LOCK_WAIT_SECONDS  = 1;
    private static final long LOCK_LEASE_SECONDS = 5;

    private final RedissonClient redissonClient;
    private final InventoryStore inventoryStore;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final FlashSaleProperties properties;

    public FlashSaleService(RedissonClient redissonClient,
                            InventoryStore inventoryStore,
                            KafkaTemplate<String, Object> kafkaTemplate,
                            MeterRegistry meterRegistry,
                            FlashSaleProperties properties) {
        this.redissonClient = redissonClient;
        this.inventoryStore = inventoryStore;
        this.kafkaTemplate  = kafkaTemplate;
        this.meterRegistry  = meterRegistry;
        this.properties     = properties;
    }

    public OrderResult placeOrder(String userId, String productId) {
        if (!checkRateLimit(userId)) {
            log.warn("Rate limit exceeded: userId={}", userId);
            record("rate_limited");
            return OrderResult.RATE_LIMITED;
        }

        // Per-user lock prevents the same user from placing concurrent duplicate orders.
        // Inventory safety is the responsibility of the atomic Lua script — not this lock.
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + userId + ":" + productId);

        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Concurrent request blocked: userId={} productId={}", userId, productId);
                record("concurrent_block");
                return OrderResult.CONCURRENT_BLOCK;
            }

            String stockKey = STOCK_KEY_PREFIX + productId;
            long stockLeft = inventoryStore.decrementIfPositive(stockKey);

            if (stockLeft < 0) {
                log.info("Sold out: productId={}", productId);
                record("sold_out");
                return OrderResult.SOLD_OUT;
            }

            String idempotencyKey = UUID.randomUUID().toString();
            OrderEvent event = new OrderEvent(userId, productId, Instant.now(), idempotencyKey);

            return publishEvent(stockKey, event);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            record("error");
            return OrderResult.ERROR;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Publishes the order event to Kafka inside a Resilience4j circuit breaker.
     * If the breaker is open, {@link OrderResult#CIRCUIT_OPEN} is returned immediately
     * and the stock decrement is rolled back.
     */
    @CircuitBreaker(name = CB_KAFKA, fallbackMethod = "kafkaCircuitOpen")
    protected OrderResult publishEvent(String stockKey, OrderEvent event) {
        long timeoutSeconds = properties.kafka().sendTimeoutSeconds();
        try {
            kafkaTemplate.send(properties.kafka().ordersTopic(), event.userId(), event)
                         .get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException kafkaEx) {
            inventoryStore.increment(stockKey);
            Throwable cause = kafkaEx instanceof ExecutionException ex ? ex.getCause() : kafkaEx;
            log.error("Kafka send failed, stock rolled back for idempotencyKey={}",
                      event.idempotencyKey(), cause);
            record("error");
            return OrderResult.ERROR;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            inventoryStore.increment(stockKey);
            record("error");
            return OrderResult.ERROR;
        }

        log.info("Order accepted: userId={} productId={} stockLeft will be updated idempotencyKey={}",
                 event.userId(), event.productId(), event.idempotencyKey());
        record("accepted");
        return OrderResult.ACCEPTED;
    }

    @SuppressWarnings("unused")
    protected OrderResult kafkaCircuitOpen(String stockKey, OrderEvent event, CallNotPermittedException ex) {
        inventoryStore.increment(stockKey);
        log.warn("Circuit breaker OPEN for Kafka — stock rolled back: productId={}", event.productId());
        record("circuit_open");
        return OrderResult.CIRCUIT_OPEN;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean checkRateLimit(String userId) {
        RRateLimiter limiter = redissonClient.getRateLimiter(RATE_LIMITER_KEY_PREFIX + userId);
        limiter.trySetRate(
            RateType.OVERALL,
            properties.rateLimit().requestsPerMinute(),
            properties.rateLimit().keyTtl()
        );
        // Refresh TTL on every request: active users keep their window; idle users' keys auto-expire.
        limiter.expire(properties.rateLimit().keyTtl());
        return limiter.tryAcquire(1);
    }

    private void record(String result) {
        meterRegistry.counter("flashsale.orders", "result", result).increment();
    }
}
