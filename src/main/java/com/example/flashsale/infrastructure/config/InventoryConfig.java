package com.example.flashsale.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class InventoryConfig {

    private static final Logger log = LoggerFactory.getLogger(InventoryConfig.class);

    private final FlashSaleProperties properties;

    public InventoryConfig(FlashSaleProperties properties) {
        this.properties = properties;
    }

    @Bean
    CommandLineRunner preloadInventory(StringRedisTemplate redisTemplate) {
        return args -> {
            String key   = "stock:product:" + properties.inventory().defaultProductId();
            String stock = String.valueOf(properties.inventory().initialStock());

            // SETNX: no-op if the key already exists so stock surviving a restart is never overwritten.
            Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(key, stock);

            if (Boolean.TRUE.equals(wasSet)) {
                log.info("Inventory initialized: {} = {}", key, stock);
            } else {
                String current = redisTemplate.opsForValue().get(key);
                log.info("Inventory already present, not overwritten: {} = {}", key, current);
            }
        };
    }
}
