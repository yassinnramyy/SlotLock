CREATE TABLE waitlist_entries (
                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                  resource_id BIGINT NOT NULL,
                                  requested_start_at DATETIME NOT NULL,
                                  requested_end_at DATETIME NOT NULL,
                                  customer_id BIGINT NOT NULL,
                                  status VARCHAR(20) NOT NULL DEFAULT 'WAITING',   -- WAITING, PROMOTED, EXPIRED
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT fk_waitlist_resource FOREIGN KEY (resource_id) REFERENCES resources(id),
                                  CONSTRAINT fk_waitlist_customer FOREIGN KEY (customer_id) REFERENCES users(id)
);