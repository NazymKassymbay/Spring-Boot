package com.example.shop.order.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link OrderPlaced} onto Kafka (lecture week 11, slide 25).
 *
 * <p>The message key is the order id, so every event of one order lands on the same partition and is read
 * in the order it happened. {@code send()} is asynchronous: we attach a callback that logs a failure rather
 * than block the caller. The caller here is {@link OrderEventsRelay}, which runs only after the order
 * transaction has committed, so the order itself is safe even if Kafka is momentarily unreachable.
 */
@Component
public class OrderEventsProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsProducer.class);

    private final KafkaTemplate<String, OrderPlaced> kafka;

    public OrderEventsProducer(KafkaTemplate<String, OrderPlaced> kafka) {
        this.kafka = kafka;
    }

    public void publish(OrderPlaced event) {
        String key = String.valueOf(event.orderId());
        kafka.send(OrderEventsConfig.ORDERS_PLACED_TOPIC, key, event)
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        // The order is already committed; a lost event is logged, not fatal.
                        // A transactional outbox (slide 13-14) would retry it -- noted as future work for SIS 4.
                        log.error("Failed to publish OrderPlaced for order {}: {}",
                                event.orderId(), failure.getMessage());
                    } else {
                        log.info("Published OrderPlaced eventId={} order={} to partition {}",
                                event.eventId(), event.orderId(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}
