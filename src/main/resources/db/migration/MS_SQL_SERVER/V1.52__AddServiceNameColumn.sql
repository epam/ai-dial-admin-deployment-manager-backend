ALTER TABLE deployment ADD service_name VARCHAR(255);
GO
CREATE UNIQUE NONCLUSTERED INDEX idx_deployment_service_name ON deployment(service_name) WHERE service_name IS NOT NULL;
