package com.example.flashsale.concurrency;

import com.example.flashsale.application.FlashSaleService;
import com.example.flashsale.domain.OrderResult;
import com.example.flashsale.infrastructure.config.FlashSaleProperties;
import com.example.flashsale.infrastructure.redis.InventoryStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies no overselling occurs when many concurrent threads race to buy the same product.
 * Uses AtomicLong CAS to simulate the atomicity guarantee of the Redis Lua script.
 */
@ExtendWith(MockitoExtension.class)
class OversellConcurrencyTest {

    private static final int    STOCK        = 100;
    private static final int    THREAD_COUNT = 500;
    private static final String PRODUCT_ID   = "IPHONE15";

    @Mock private RedissonClient redissonClient;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private RRateLimiter rateLimiter;

    private FlashSaleService service;

    @BeforeEach
    void setUp() throws Exception {
        AtomicLong stock = new AtomicLong(STOCK);

        // InventoryStore backed by a CAS loop — same atomicity semantics as the Redis Lua script
        InventoryStore concurrentStore = new InventoryStore() {
            @Override
            public long decrementIfPositive(String key) {
                while (true) {
                    long current = stock.get();
                    if (current <= 0) return -1L;
                    long next = current - 1;
                    if (stock.compareAndSet(current, next)) return next;
                }
            }

            @Override
            public void increment(String key) {
                stock.incrementAndGet();
            }
        };

        FlashSaleProperties properties = new FlashSaleProperties(
                new FlashSaleProperties.Security("test-key"),
                new FlashSaleProperties.RateLimit(1000, Duration.ofMinutes(2)),
                new FlashSaleProperties.Inventory("IPHONE15", STOCK),
                new FlashSaleProperties.Kafka("flash-sale-orders", 5L)
        );

        service = new FlashSaleService(redissonClient, concurrentStore, kafkaTemplate,
                                       new SimpleMeterRegistry(), properties);

        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);

        // Each user gets their own lock (per user+product key) — no contention between users
        when(redissonClient.getLock(anyString())).thenAnswer(inv -> {
            RLock mockLock = mock(RLock.class);
            when(mockLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(mockLock.isHeldByCurrentThread()).thenReturn(true);
            return mockLock;
        });

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, Object>> okFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(okFuture);
    }

    @Test
    void noOversell_under500ConcurrentBuyers() throws InterruptedException {
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger soldOut  = new AtomicInteger();

        ExecutorService pool  = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch  latch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch  start = new CountDownLatch(1);

        for (int i = 0; i < THREAD_COUNT; i++) {
            String userId = "user" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    OrderResult r = service.placeOrder(userId, PRODUCT_ID);
                    if (r == OrderResult.ACCEPTED) accepted.incrementAndGet();
                    else if (r == OrderResult.SOLD_OUT) soldOut.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        start.countDown();
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(completed).as("All threads must finish within 30s").isTrue();
        assertThat(accepted.get())
                .as("Accepted orders must never exceed initial stock")
                .isLessThanOrEqualTo(STOCK);
        assertThat(accepted.get() + soldOut.get())
                .as("Every thread must get a definite result")
                .isEqualTo(THREAD_COUNT);

        System.out.printf("[OversellTest] Stock=%d  Accepted=%d  SoldOut=%d  Lost=%d%n",
                          STOCK, accepted.get(), soldOut.get(),
                          THREAD_COUNT - accepted.get() - soldOut.get());
    }
}
