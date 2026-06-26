package com.example.flashsale.infrastructure.kafka;

import com.example.flashsale.domain.Order;
import com.example.flashsale.domain.OrderEvent;
import com.example.flashsale.domain.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer that persists accepted flash-sale orders to PostgreSQL.
 *
 * <p><strong>Idempotency:</strong> Each event carries a UUID {@code idempotencyKey}.
 * A unique database constraint on that column means that if Kafka redelivers a message
 * (at-least-once semantics), the second save raises a {@link DataIntegrityViolationException}
 * which is caught here and silently discarded — the order already exists.
 *
 * <p><strong>Error handling:</strong> Any other exception propagates to Spring Kafka's
 * {@code DefaultErrorHandler}, which retries with exponential back-off and eventually
 * routes the poison message to {@code flash-sale-orders.DLT}.
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderRepository orderRepository;
    private final MeterRegistry meterRegistry;

    public OrderEventConsumer(OrderRepository orderRepository, MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.meterRegistry   = meterRegistry;
    }

    @KafkaListener(topics = "${flashsale.kafka.orders-topic:flash-sale-orders}", groupId = "flash-sale-group")
    @Transactional
    public void consume(OrderEvent event) {
        log.info("Consuming event: userId={} productId={} idempotencyKey={}",
                 event.userId(), event.productId(), event.idempotencyKey());

        Order order = new Order();
        order.setUserId(event.userId());
        order.setProductId(event.productId());
        order.setStatus("CONFIRMED");
        order.setIdempotencyKey(event.idempotencyKey());

        try {
            orderRepository.saveAndFlush(order);
            log.info("Order persisted: idempotencyKey={}", event.idempotencyKey());
            meterRegistry.counter("flashsale.consumer.orders", "status", "saved").increment();
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate event ignored (idempotent): idempotencyKey={}", event.idempotencyKey());
            meterRegistry.counter("flashsale.consumer.orders", "status", "duplicate").increment();
        }
    }
}
