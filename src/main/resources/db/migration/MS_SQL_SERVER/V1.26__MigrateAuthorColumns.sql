-- Add author column to deployment table
alter table deployment
    add author nvarchar(max);
go

-- Migrate data from author_email to author column for deployment table
update deployment
set author = author_email
where author_email is not null;
go

-- Add author column to image_definition table
alter table image_definition
    add author nvarchar(max);
go

-- Migrate data from author_email to author column for image_definition table
update image_definition
set author = author_email
where author_email is not null;
go

-- Add author column to built_image table
alter table built_image
    add author nvarchar(max);
go

-- Migrate data from author_email to author column for built_image table
update built_image
set author = author_email
where author_email is not null;
go

-- Drop old author_name and author_email columns from deployment table
alter table deployment
    drop column author_name;
go

alter table deployment
    drop column author_email;
go

-- Drop old author_name and author_email columns from image_definition table
alter table image_definition
    drop column author_name;
go

alter table image_definition
    drop column author_email;
go

-- Drop old author_name and author_email columns from built_image table
alter table built_image
    drop column author_name;
go

alter table built_image
    drop column author_email;
go

