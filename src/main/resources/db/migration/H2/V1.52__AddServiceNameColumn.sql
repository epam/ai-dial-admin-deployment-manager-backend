ALTER TABLE deployment ADD COLUMN service_name VARCHAR(63);
CREATE UNIQUE INDEX idx_deployment_service_name ON deployment(service_name);
