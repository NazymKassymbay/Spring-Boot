package com.example.shop.common.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.example.shop.order.events.InvalidOrderEventException;
import com.example.shop.order.events.OrderEventsConfig;

/**
 * Kafka error handling (lecture week 11, slide 28): retry a failing record a couple of times, then park it.
 *
 * <p>When a {@code @KafkaListener} throws, this handler retries the same record twice with a one-second pause
 * ({@link FixedBackOff}). If it still fails, the {@link DeadLetterPublishingRecoverer} republishes it to
 * {@code <topic>.DLT} with the failure details in the headers, and the consumer moves on instead of blocking
 * the partition. A structurally broken record ({@link InvalidOrderEventException}) is non-retryable and goes
 * straight to the dead-letter topic.
 *
 * <p>Spring Boot wires this single {@code DefaultErrorHandler} bean into every listener container.
 */
@Configuration
public class KafkaConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        // Park a failed record on our own dead-letter topic, keeping its original partition. The default
        // resolver would derive the name as "<topic>-dlt"; we name it explicitly so it matches the
        // orders.placed.DLT topic declared in OrderEventsConfig.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) ->
                        new TopicPartition(OrderEventsConfig.ORDERS_PLACED_DLT, record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        handler.addNotRetryableExceptions(InvalidOrderEventException.class);
        return handler;
    }
}
