package com.example.shop.order.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * SIS 3 end-to-end check of the Kafka flow (lecture week 11) against an in-JVM broker and a real PostgreSQL.
 * It proves the three behaviours the assignment asks for: the event is produced and consumed, the consumer is
 * idempotent under a duplicate delivery, and a poison record is parked on the dead-letter topic.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3, topics = {"orders.placed", "orders.placed.DLT"})
@ActiveProfiles("test")
@Testcontainers
class OrderEventsIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private OrderEventsProducer producer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    // The side effect is mocked so we can count exactly how many times the consumer acted on an event.
    @MockitoBean
    private OrderNotifier notifier;

    @Test
    void placedOrderEventIsConsumedAndRecorded() {
        OrderPlaced event = sampleEvent(101L);

        producer.publish(event);

        verify(notifier, timeout(10_000)).orderConfirmed(eq(101L), eq("buyer@example.com"), anyInt());
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, event.eventId());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void duplicateDeliveryIsProcessedOnlyOnce() {
        OrderPlaced event = sampleEvent(102L);

        producer.publish(event);
        producer.publish(event);   // same eventId -> the second one is a duplicate

        // Give both deliveries time to arrive, then assert the side effect happened exactly once.
        verify(notifier, timeout(10_000)).orderConfirmed(eq(102L), eq("buyer@example.com"), anyInt());
        verify(notifier, timeout(2_000).times(1)).orderConfirmed(eq(102L), eq("buyer@example.com"), anyInt());
    }

    @Test
    void poisonMessageGoesToDeadLetterTopic() {
        // orderId <= 0 can never be a real placed order; the consumer rejects it as non-retryable.
        OrderPlaced poison = new OrderPlaced(UUID.randomUUID(), -1L, "Nobody",
                "nobody@example.com", BigDecimal.ONE, Instant.now());

        try (Consumer<String, String> dltConsumer = deadLetterConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(dltConsumer, "orders.placed.DLT");

            producer.publish(poison);

            ConsumerRecord<String, String> parked =
                    KafkaTestUtils.getSingleRecord(dltConsumer, "orders.placed.DLT", Duration.ofSeconds(10));
            assertThat(parked.key()).isEqualTo("-1");
            assertThat(parked.value()).contains("\"orderId\":-1");
        }

        verify(notifier, never()).orderConfirmed(eq(-1L), org.mockito.ArgumentMatchers.anyString(), anyInt());
    }

    private OrderPlaced sampleEvent(long orderId) {
        return new OrderPlaced(UUID.randomUUID(), orderId, "Buyer",
                "buyer@example.com", new BigDecimal("649990.00"), Instant.now());
    }

    private Consumer<String, String> deadLetterConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
    }
}
