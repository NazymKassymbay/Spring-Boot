-- SIS 3 (lecture week 11, slide 23): the idempotency store for Kafka consumers.
-- Each consumed event id is written once; a duplicate delivery hits the primary key and is skipped,
-- which keeps the consumer idempotent under Kafka's at-least-once delivery.

CREATE TABLE processed_events (
    event_id   UUID                     PRIMARY KEY,
    handled_at TIMESTAMP WITH TIME ZONE NOT NULL
);
