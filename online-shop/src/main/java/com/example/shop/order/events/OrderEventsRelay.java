package com.example.shop.order.events;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the in-process Spring event to Kafka (lecture week 11, slides 12-13).
 *
 * <p>{@link com.example.shop.order.app.OrderService} publishes {@link OrderPlaced} as a Spring application
 * event <em>inside</em> the order transaction. This relay reacts with {@code @TransactionalEventListener} in
 * the {@code AFTER_COMMIT} phase, so Kafka only ever sees events for orders that were actually committed:
 * if the transaction rolls back, nothing is sent.
 *
 * <p>This is the "commit, then send" approach. Its one remaining gap -- a crash between commit and send --
 * is closed by a transactional outbox, which the lecture presents as the next step and which this project
 * leaves as documented future work.
 */
@Component
public class OrderEventsRelay {

    private final OrderEventsProducer producer;

    public OrderEventsRelay(OrderEventsProducer producer) {
        this.producer = producer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlaced event) {
        producer.publish(event);
    }
}
