package com.example.flashsale.infrastructure.health;

import com.example.flashsale.infrastructure.config.FlashSaleProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class FlashSaleHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redisTemplate;
    private final FlashSaleProperties properties;

    public FlashSaleHealthIndicator(StringRedisTemplate redisTemplate,
                                    FlashSaleProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties    = properties;
    }

    @Override
    public Health health() {
        try {
            String key   = "stock:product:" + properties.inventory().defaultProductId();
            String stock = redisTemplate.opsForValue().get(key);
            long   level = stock != null ? Long.parseLong(stock) : -1;

            if (level < 0) {
                return Health.unknown()
                             .withDetail("product", properties.inventory().defaultProductId())
                             .withDetail("stock", "key not found")
                             .build();
            }
            if (level == 0) {
                return Health.status("SOLD_OUT")
                             .withDetail("product", properties.inventory().defaultProductId())
                             .withDetail("stock", 0)
                             .build();
            }
            return Health.up()
                         .withDetail("product", properties.inventory().defaultProductId())
                         .withDetail("stock", level)
                         .build();
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}
