-- SIS 3 (lecture week 11, slide 23): the idempotency store for Kafka consumers.
-- Each consumed event id is written once; a duplicate delivery hits the primary key and is skipped,
-- which keeps the consumer idempotent under Kafka's at-least-once delivery.
--
-- Version 3, not 2: in the dev profile Flyway also reads db/seed, where V2__seed_demo_catalogue.sql already
-- owns version 2 (two files with one version stop start-up). Test and prod have no V2, which Flyway allows.

CREATE TABLE processed_events (
    event_id   UUID                     PRIMARY KEY,
    handled_at TIMESTAMP WITH TIME ZONE NOT NULL
);
