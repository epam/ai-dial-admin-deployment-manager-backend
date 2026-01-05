create table image_definition
(
    id                      uuid default random_uuid(),
    name                    varchar(255) not null unique,
    description             text,
    source                  json         not null,
    metadata                json         not null,
    license                 varchar(255),

    created_at              timestamp(3) not null,
    updated_at              timestamp(3) not null,
    topics                  varchar(32) array,

    last_built_image_id     uuid,
    last_built_image_status varchar(32) not null,

    primary key (id)
);