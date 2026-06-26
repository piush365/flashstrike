package com.example.flashsale.application;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class FlashSaleServiceTest {

    @Mock private RedissonClient redissonClient;
    @Mock private InventoryStore inventoryStore;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private RRateLimiter rateLimiter;
    @Mock private RLock lock;

    private FlashSaleService service;

    @BeforeEach
    void setUp() throws Exception {
        FlashSaleProperties properties = new FlashSaleProperties(
                new FlashSaleProperties.Security("test-key"),
                new FlashSaleProperties.RateLimit(3, Duration.ofMinutes(2)),
                new FlashSaleProperties.Inventory("IPHONE15", 100),
                new FlashSaleProperties.Kafka("flash-sale-orders", 5L)
        );

        service = new FlashSaleService(redissonClient, inventoryStore, kafkaTemplate,
                                       new SimpleMeterRegistry(), properties);

        lenient().when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().when(rateLimiter.tryAcquire(1)).thenReturn(true);
        lenient().when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void placeOrder_returnsSoldOut_whenStockExhausted() {
        when(inventoryStore.decrementIfPositive(anyString())).thenReturn(-1L);

        assertThat(service.placeOrder("user1", "IPHONE15")).isEqualTo(OrderResult.SOLD_OUT);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void placeOrder_returnsAccepted_whenStockAvailable() {
        when(inventoryStore.decrementIfPositive(anyString())).thenReturn(99L);
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, Object>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        assertThat(service.placeOrder("user1", "IPHONE15")).isEqualTo(OrderResult.ACCEPTED);
    }

    @Test
    void placeOrder_returnsRateLimited_whenRateLimitExceeded() throws Exception {
        when(rateLimiter.tryAcquire(1)).thenReturn(false);

        assertThat(service.placeOrder("user1", "IPHONE15")).isEqualTo(OrderResult.RATE_LIMITED);
        verify(lock, never()).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void placeOrder_returnsConcurrentBlock_whenLockNotAcquired() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        assertThat(service.placeOrder("user1", "IPHONE15")).isEqualTo(OrderResult.CONCURRENT_BLOCK);
        verify(inventoryStore, never()).decrementIfPositive(anyString());
    }

    @Test
    void placeOrder_rollsBackStock_whenKafkaFails() {
        when(inventoryStore.decrementIfPositive(anyString())).thenReturn(5L);

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture);

        assertThat(service.placeOrder("user1", "IPHONE15")).isEqualTo(OrderResult.ERROR);
        verify(inventoryStore).increment("stock:product:IPHONE15");
    }

    @Test
    void placeOrder_releasesLock_evenOnException() throws Exception {
        when(inventoryStore.decrementIfPositive(anyString()))
                .thenThrow(new RuntimeException("Redis error"));

        try {
            service.placeOrder("user1", "IPHONE15");
        } catch (Exception ignored) { }

        verify(lock).unlock();
    }
}
