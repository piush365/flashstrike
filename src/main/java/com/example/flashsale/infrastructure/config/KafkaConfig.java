package com.example.flashsale.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    private final FlashSaleProperties properties;

    public KafkaConfig(FlashSaleProperties properties) {
        this.properties = properties;
    }

    @Bean
    public NewTopic flashSaleOrdersTopic() {
        return TopicBuilder.name(properties.kafka().ordersTopic())
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic flashSaleOrdersDltTopic() {
        return TopicBuilder.name(properties.kafka().ordersTopic() + ".DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Retry with exponential back-off (1s → 2s → 4s … up to 30s total),
     * then publish the poison message to the Dead Letter Topic.
     * DataIntegrityViolationException is non-retryable: the order is a duplicate
     * and already handled silently in OrderEventConsumer.
     */
    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        var backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxElapsedTime(30_000L);

        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(org.springframework.dao.DataIntegrityViolationException.class);
        return handler;
    }
}
