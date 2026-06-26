package com.example.flashsale.infrastructure.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisInventoryStore implements InventoryStore {

    private static final RedisScript<Long> DECR_IF_POSITIVE = RedisScript.of(
        "local val = redis.call('GET', KEYS[1])\n" +
        "if not val or tonumber(val) <= 0 then return -1 end\n" +
        "return redis.call('DECR', KEYS[1])",
        Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisInventoryStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long decrementIfPositive(String productKey) {
        Long result = redisTemplate.execute(DECR_IF_POSITIVE, List.of(productKey));
        return result != null ? result : -1L;
    }

    @Override
    public void increment(String productKey) {
        redisTemplate.opsForValue().increment(productKey);
    }
}
