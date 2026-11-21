package com.example.shop.order.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer of {@link OrderPlaced} (lecture week 11, slide 26).
 *
 * <p>It runs in its own consumer group, so it is independent of any other consumer of the same topic.
 * {@code concurrency = "3"} matches the three partitions -- one thread each, no more. The whole method runs
 * in one transaction, so the de-duplication row and the side effect commit together.
 *
 * <p>The flow is: validate the record (a structurally broken one is rejected as non-retryable and parked on
 * the dead-letter topic), then skip it if we have already handled this {@code eventId}, otherwise perform
 * the side effect. This makes the consumer idempotent under at-least-once delivery.
 */
@Component
public class OrderPlacedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacedListener.class);

    private final ProcessedEvents processed;
    private final OrderNotifier notifier;

    public OrderPlacedListener(ProcessedEvents processed, OrderNotifier notifier) {
        this.processed = processed;
        this.notifier = notifier;
    }

    @KafkaListener(
            topics = OrderEventsConfig.ORDERS_PLACED_TOPIC,
            groupId = "order-notifications",
            concurrency = "3")
    @Transactional
    public void on(OrderPlaced event, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        validate(event);

        if (!processed.markIfNew(event.eventId())) {
            log.info("Duplicate OrderPlaced eventId={} order={} ignored", event.eventId(), event.orderId());
            return;
        }

        notifier.orderConfirmed(event.orderId(), event.customerEmail(), partition);
    }

    // A fact that cannot be true for a real placed order means the record is broken: reject it so the error
    // handler sends it straight to the dead-letter topic rather than retrying forever.
    private void validate(OrderPlaced event) {
        if (event.eventId() == null) {
            throw new InvalidOrderEventException("OrderPlaced without an eventId");
        }
        if (event.orderId() <= 0) {
            throw new InvalidOrderEventException("OrderPlaced with a non-positive orderId: " + event.orderId());
        }
        if (event.total() == null || event.total().signum() < 0) {
            throw new InvalidOrderEventException("OrderPlaced with an invalid total: " + event.total());
        }
    }
}
