-- Add image_builder column to image_definition table
ALTER TABLE image_definition ADD image_builder VARCHAR(32) NOT NULL DEFAULT 'KANIKO';
