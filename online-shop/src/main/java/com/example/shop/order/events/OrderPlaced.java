package com.example.shop.order.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.shop.order.domain.Order;

/**
 * Domain event (lecture week 11, slides 9-10): a fact named in the past tense -- an order HAS BEEN placed.
 * It is published in two steps: first as a Spring application event inside the order transaction, then,
 * once that transaction has committed, onto the Kafka topic {@code orders.placed} by {@link OrderEventsRelay}.
 *
 * <p>The event carries its own {@code eventId} so that consumers can de-duplicate it (events are delivered
 * at least once), {@code orderId} as the aggregate id used for the Kafka message key, and enough data that a
 * consumer never has to call back into the order service.
 *
 * @param eventId     unique id of this event, used by consumers to skip duplicates
 * @param orderId     aggregate id; also the Kafka partition key, so all events of one order stay ordered
 * @param customerName  who placed the order
 * @param customerEmail where a confirmation would be sent
 * @param total       order total at the moment it was placed
 * @param occurredAt  when the order was placed
 */
public record OrderPlaced(
        UUID eventId,
        long orderId,
        String customerName,
        String customerEmail,
        BigDecimal total,
        Instant occurredAt) {

    // Builds the event from a freshly saved order. A new eventId is minted for every publication.
    public static OrderPlaced from(Order order, Instant occurredAt) {
        return new OrderPlaced(
                UUID.randomUUID(),
                order.getId(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getTotalAmount(),
                occurredAt);
    }
}
