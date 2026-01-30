-- Add image_builder column to image_definition table
ALTER TABLE image_definition ADD COLUMN IF NOT EXISTS image_builder VARCHAR(32) NOT NULL DEFAULT 'KANIKO';
