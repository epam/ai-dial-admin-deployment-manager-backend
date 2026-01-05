ALTER TABLE deployment ADD COLUMN container_ports TEXT;

UPDATE deployment
SET container_ports = container_port::TEXT
WHERE container_port IS NOT NULL;

ALTER TABLE deployment DROP COLUMN container_port;