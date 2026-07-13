CREATE TABLE resources (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           tenant_id BIGINT NOT NULL,
                           name VARCHAR(255) NOT NULL,
                           description VARCHAR(500),
                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_resources_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE TABLE availability_windows (
                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      resource_id BIGINT NOT NULL,
                                      day_of_week TINYINT NOT NULL,   -- 0=Sunday..6=Saturday
                                      start_time TIME NOT NULL,
                                      end_time TIME NOT NULL,
                                      CONSTRAINT fk_avail_resource FOREIGN KEY (resource_id) REFERENCES resources(id)
);