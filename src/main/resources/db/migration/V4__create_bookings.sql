CREATE TABLE bookings (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          slot_id BIGINT NOT NULL,
                          customer_id BIGINT NOT NULL,
                          status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',   -- CONFIRMED, CANCELLED
                          idempotency_key VARCHAR(100) NOT NULL UNIQUE,
                          version BIGINT NOT NULL DEFAULT 0,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT fk_bookings_slot FOREIGN KEY (slot_id) REFERENCES slots(id),
                          CONSTRAINT fk_bookings_customer FOREIGN KEY (customer_id) REFERENCES users(id)
);