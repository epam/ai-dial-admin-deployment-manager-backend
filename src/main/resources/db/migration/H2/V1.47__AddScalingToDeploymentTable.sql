-- Add scaling column to base deployment table
ALTER TABLE deployment ADD COLUMN scaling JSON;

-- Migrate existing inference scaling data to base deployment table
UPDATE deployment d SET scaling = (SELECT scaling FROM inference_deployment c WHERE c.id = d.id) WHERE d.id IN (SELECT id FROM inference_deployment WHERE scaling IS NOT NULL);

-- Drop scaling column from inference_deployment (added in V1.43)
ALTER TABLE inference_deployment DROP COLUMN scaling;

-- Drop deprecated scale columns from deployment table
ALTER TABLE deployment DROP COLUMN initial_scale;
ALTER TABLE deployment DROP COLUMN min_scale;
ALTER TABLE deployment DROP COLUMN max_scale;
