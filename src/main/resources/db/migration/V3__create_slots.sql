CREATE TABLE slots (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       resource_id BIGINT NOT NULL,
                       start_at DATETIME NOT NULL,
                       end_at DATETIME NOT NULL,
                       status VARCHAR(20) NOT NULL DEFAULT 'OPEN',   -- OPEN, BOOKED, CANCELLED
                       version BIGINT NOT NULL DEFAULT 0,
                       CONSTRAINT fk_slots_resource FOREIGN KEY (resource_id) REFERENCES resources(id),
                       UNIQUE KEY uq_resource_start (resource_id, start_at)
);