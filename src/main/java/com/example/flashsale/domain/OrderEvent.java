package com.example.flashsale.domain;

import java.time.Instant;

public record OrderEvent(
        String userId,
        String productId,
        Instant timestamp,
        String idempotencyKey
) {}
