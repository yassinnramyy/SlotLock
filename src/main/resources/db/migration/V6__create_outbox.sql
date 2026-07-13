CREATE TABLE outbox_events (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               aggregate_type VARCHAR(50) NOT NULL,
                               aggregate_id BIGINT NOT NULL,
                               event_type VARCHAR(50) NOT NULL,
                               payload JSON NOT NULL,
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING, SENT, FAILED
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               sent_at TIMESTAMP NULL
);