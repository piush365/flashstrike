package com.example.flashsale;

import com.example.flashsale.domain.OrderRepository;
import com.example.flashsale.infrastructure.redis.InventoryStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies the Spring application context loads without errors.
 * Infrastructure dependencies are replaced with mocks so this test
 * runs without external services.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
        "org.redisson.spring.starter.RedissonAutoConfigurationV2",
    "flashsale.security.api-key=test-key"
})
class FlashsaleApplicationTests {

    @MockBean OrderRepository orderRepository;
    @MockBean InventoryStore inventoryStore;
    @MockBean KafkaTemplate<String, Object> kafkaTemplate;
    @MockBean(answer = Answers.RETURNS_DEEP_STUBS) StringRedisTemplate stringRedisTemplate;

    @TestConfiguration
    static class TestInfraConfig {

        @Bean
        @Primary
        public MeterRegistry testMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        /**
         * RETURNS_DEEP_STUBS so that redissonClient.getConfig().isClusterConfig()
         * and similar chains return safe defaults instead of NPE.
         * @ConditionalOnMissingBean on RedissonConfig prevents double instantiation.
         */
        @Bean
        @Primary
        public RedissonClient mockRedissonClient() {
            return Mockito.mock(RedissonClient.class, Answers.RETURNS_DEEP_STUBS);
        }
    }

    @Test
    void contextLoads() {
        // Verifies Spring wires all beans without exceptions.
    }
}
