package com.example.flashsale.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Type-safe binding for all flash-sale application properties.
 * Validated on startup; the application will refuse to start if any required
 * property is absent or out of range.
 */
@ConfigurationProperties(prefix = "flashsale")
public record FlashSaleProperties(
        Security security,
        RateLimit rateLimit,
        Inventory inventory,
        Kafka kafka
) {

    public record Security(String apiKey) {}

    public record RateLimit(
            @DefaultValue("3")  int requestsPerMinute,
            @DefaultValue("PT2M") Duration keyTtl
    ) {}

    public record Inventory(
            @DefaultValue("IPHONE15") String defaultProductId,
            @DefaultValue("100")      int initialStock
    ) {}

    public record Kafka(
            @DefaultValue("flash-sale-orders") String ordersTopic,
            @DefaultValue("5")                 long sendTimeoutSeconds
    ) {}
}
