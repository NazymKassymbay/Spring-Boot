package com.example.shop.order.events;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * De-duplication store for consumed events (lecture week 11, slide 23).
 *
 * <p>Kafka delivers at least once, so the same event can arrive twice (for example after a consumer crash
 * between processing and the offset commit). Each handled {@code eventId} is written once; a second attempt
 * to write it is a no-op, which lets the consumer recognise and skip a duplicate.
 *
 * <p>The insert uses PostgreSQL {@code ON CONFLICT DO NOTHING} and runs inside the consumer's transaction,
 * so the de-duplication row and the consumer's own work commit together (slide 26).
 */
@Component
public class ProcessedEvents {

    private final JdbcTemplate jdbc;

    public ProcessedEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records the event as handled. Returns {@code true} if this is the first time we see it
     * (the row was inserted) and {@code false} if it was already processed (duplicate).
     */
    public boolean markIfNew(UUID eventId) {
        int inserted = jdbc.update(
                "INSERT INTO processed_events (event_id, handled_at) VALUES (?, now()) ON CONFLICT DO NOTHING",
                eventId);
        return inserted > 0;
    }
}
