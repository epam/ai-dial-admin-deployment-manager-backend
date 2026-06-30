ALTER TABLE inference_deployment ADD COLUMN inference_task VARCHAR(32);
ALTER TABLE inference_deployment_aud ADD COLUMN inference_task VARCHAR(255);
