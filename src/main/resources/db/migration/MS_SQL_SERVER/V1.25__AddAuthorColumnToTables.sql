-- Add author_name and author_email columns to deployment table
alter table deployment
    add author_name nvarchar(max);
alter table deployment
    add author_email nvarchar(max);

-- Add author_name and author_email columns to image_definition table
alter table image_definition
    add author_name nvarchar(max);
alter table image_definition
    add author_email nvarchar(max);

-- Add author_name and author_email columns to built_image table
alter table built_image
    add author_name nvarchar(max);
alter table built_image
    add author_email nvarchar(max);
