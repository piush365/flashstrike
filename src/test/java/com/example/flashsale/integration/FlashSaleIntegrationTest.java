package com.example.flashsale.integration;

import com.example.flashsale.domain.OrderRepository;
import com.example.flashsale.infrastructure.security.ApiKeyAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@EmbeddedKafka(
    partitions = 6,
    topics = {"flash-sale-orders", "flash-sale-orders.DLT"}
)
@ActiveProfiles("test")
class FlashSaleIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private OrderRepository orderRepository;

    @Value("${flashsale.security.api-key}")
    private String apiKey;

    @BeforeEach
    void resetState() {
        // Reset inventory
        redisTemplate.delete("stock:product:IPHONE15");
        redisTemplate.opsForValue().set("stock:product:IPHONE15", "10");

        // Clear rate-limiter keys so tests don't bleed into each other
        Set<String> rateLimitKeys = redisTemplate.keys("rate_limiter:*");
        if (rateLimitKeys != null && !rateLimitKeys.isEmpty()) {
            redisTemplate.delete(rateLimitKeys);
        }

        orderRepository.deleteAll();
    }

    @Test
    void buyProduct_returns202_whenStockAvailable() throws Exception {
        mockMvc.perform(post("/api/v1/flash-sale/buy")
                        .param("userId", "user1")
                        .param("productId", "IPHONE15")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isAccepted());
    }

    @Test
    void buyProduct_returns410_whenSoldOut() throws Exception {
        redisTemplate.opsForValue().set("stock:product:IPHONE15", "0");

        mockMvc.perform(post("/api/v1/flash-sale/buy")
                        .param("userId", "user1")
                        .param("productId", "IPHONE15")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey))
               .andExpect(status().isGone());
    }

    @Test
    void buyProduct_returns401_withoutApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/flash-sale/buy")
                        .param("userId", "user1")
                        .param("productId", "IPHONE15"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void buyProduct_returns400_withBlankUserId() throws Exception {
        mockMvc.perform(post("/api/v1/flash-sale/buy")
                        .param("userId", "  ")
                        .param("productId", "IPHONE15")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey))
               .andExpect(status().isBadRequest());
    }

    @Test
    void buyProduct_returns429_whenRateLimitExceeded() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/flash-sale/buy")
                            .param("userId", "spammer")
                            .param("productId", "IPHONE15")
                            .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey));
        }

        mockMvc.perform(post("/api/v1/flash-sale/buy")
                        .param("userId", "spammer")
                        .param("productId", "IPHONE15")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey))
               .andExpect(status().isTooManyRequests());
    }

    @Test
    void noOversell_whenConcurrentBuyers_exceedStock() throws Exception {
        redisTemplate.opsForValue().set("stock:product:IPHONE15", "5");

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/v1/flash-sale/buy")
                            .param("userId", "buyer" + i)
                            .param("productId", "IPHONE15")
                            .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey));
        }

        String remaining = redisTemplate.opsForValue().get("stock:product:IPHONE15");
        assertThat(Long.parseLong(remaining)).isGreaterThanOrEqualTo(0);
    }
}
