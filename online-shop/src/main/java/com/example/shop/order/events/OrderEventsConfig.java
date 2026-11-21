package com.example.shop.order.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topics for the order context (lecture week 11, slide 25).
 * KafkaAdmin, auto-configured by Spring Boot, creates any missing topic at start-up when a broker is reachable;
 * if no broker is running the application still starts and these beans are simply not applied.
 *
 * <p>{@code orders.placed} is keyed by order id, so three partitions give parallelism while keeping every
 * event of a single order in order. {@code orders.placed.DLT} is the dead-letter topic where the error
 * handler parks records it could not process (slide 28); it has the same partition count so a parked record
 * keeps its original partition.
 */
@Configuration
public class OrderEventsConfig {

    public static final String ORDERS_PLACED_TOPIC = "orders.placed";
    public static final String ORDERS_PLACED_DLT = "orders.placed.DLT";

    @Bean
    NewTopic ordersPlacedTopic() {
        return TopicBuilder.name(ORDERS_PLACED_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic ordersPlacedDeadLetterTopic() {
        return TopicBuilder.name(ORDERS_PLACED_DLT).partitions(3).replicas(1).build();
    }
}
