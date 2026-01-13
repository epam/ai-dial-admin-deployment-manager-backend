-- Add index on image_definition.name
CREATE INDEX idx_image_definition_name ON image_definition(name);

-- Add index on deployment.image_definition_id
CREATE INDEX idx_deployment_image_definition_id ON deployment(image_definition_id);

-- Add index on disposable_resource.group_id
CREATE INDEX idx_disposable_resource_group_id ON disposable_resource(group_id);
