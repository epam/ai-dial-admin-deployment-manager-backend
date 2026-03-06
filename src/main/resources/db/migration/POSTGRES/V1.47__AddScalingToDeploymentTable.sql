-- Add scaling column to base deployment table
ALTER TABLE deployment ADD COLUMN scaling JSONB;

-- Migrate existing inference scaling data to base deployment table
UPDATE deployment d SET scaling = c.scaling FROM inference_deployment c WHERE c.id = d.id AND c.scaling IS NOT NULL;

-- Drop scaling column from inference_deployment (added in V1.43)
ALTER TABLE inference_deployment DROP COLUMN scaling;

-- Drop deprecated scale columns from deployment table
ALTER TABLE deployment DROP COLUMN initial_scale;
ALTER TABLE deployment DROP COLUMN min_scale;
ALTER TABLE deployment DROP COLUMN max_scale;
