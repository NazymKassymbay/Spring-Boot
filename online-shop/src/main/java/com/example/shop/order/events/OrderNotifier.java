package com.example.shop.order.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The side effect a consumer performs when it learns an order was placed (lecture week 11, slide 8:
 * {@code OrderPlaced -> notification-service}). In a real system this would queue a confirmation e-mail;
 * here it logs, which is enough to show that a separate consumer reacts to the event on its own schedule
 * without the order service waiting for it.
 */
@Component
public class OrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifier.class);

    public void orderConfirmed(long orderId, String customerEmail, int partition) {
        log.info("Order {} confirmed -- queued a confirmation for {} (from partition {})",
                orderId, customerEmail, partition);
    }
}
