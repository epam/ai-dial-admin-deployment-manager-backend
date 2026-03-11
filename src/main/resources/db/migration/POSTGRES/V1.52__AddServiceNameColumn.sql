ALTER TABLE deployment ADD COLUMN service_name VARCHAR(255);
CREATE UNIQUE INDEX idx_deployment_service_name ON deployment(service_name);
