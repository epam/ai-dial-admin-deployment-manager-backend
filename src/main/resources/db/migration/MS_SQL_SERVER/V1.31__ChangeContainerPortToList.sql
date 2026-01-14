ALTER TABLE deployment ADD container_ports VARCHAR(MAX);
go

UPDATE deployment
SET container_ports = CAST(container_port AS VARCHAR(MAX))
WHERE container_port IS NOT NULL;
go

ALTER TABLE deployment DROP COLUMN container_port;